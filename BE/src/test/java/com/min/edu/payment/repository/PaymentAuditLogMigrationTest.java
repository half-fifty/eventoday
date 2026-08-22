package com.min.edu.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PaymentAuditLogMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg18")
                .asCompatibleSubstituteFor("postgres")
        );

    private static Connection connection;

    @BeforeAll
    static void setUp() throws Exception {
        Flyway.configure()
            .dataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            )
            .locations("classpath:db/migration")
            .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
            .load()
            .migrate();
        connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void paymentAuditLogs_areAppendOnlyAtDatabaseLevel() throws Exception {
        long paymentOrderId = insertPaymentOrder();
        long auditLogId = insertAuditLog(paymentOrderId);

        assertThat(auditLogId).isPositive();
        assertThatThrownBy(() -> execute("UPDATE payment_audit_logs SET reason_code = 'x'"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("append-only");
        assertThatThrownBy(() -> execute("DELETE FROM payment_audit_logs"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("append-only");
        assertThatThrownBy(() -> execute("TRUNCATE payment_audit_logs"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("append-only");
    }

    @Test
    void reconciliationIndexes_existAfterConcurrentMigration() throws Exception {
        assertThat(indexExists("idx_payments_payment_order_id")).isTrue();
        assertThat(indexExists("idx_payment_orders_va_pending_created_at")).isTrue();
    }

    @Test
    void eventInventoryReleaseUpdate_rollsBackWithTransaction() throws Exception {
        long eventId = insertEventWithSoldQuantity(5);

        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(
                """
                UPDATE events
                   SET ticket_sold_quantity = ticket_sold_quantity - ?
                 WHERE id = ?
                   AND ticket_sold_quantity - ? >= 0
                """)) {
            statement.setInt(1, 2);
            statement.setLong(2, eventId);
            statement.setInt(3, 2);
            assertThat(statement.executeUpdate()).isEqualTo(1);
            connection.rollback();
        } finally {
            connection.setAutoCommit(true);
        }

        assertThat(ticketSoldQuantity(eventId)).isEqualTo(5);
    }

    private long insertPaymentOrder() throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO payment_orders (
                    order_no,
                    order_type,
                    total_amount,
                    requested_payment_method,
                    status,
                    created_at,
                    updated_at
                ) VALUES (?, 'EVENT_TICKET', ?, 'VIRTUAL_ACCOUNT', 'PENDING', now(), now())
                RETURNING id
                """)) {
            statement.setString(1, "AUDIT-MIGRATION-" + System.nanoTime());
            statement.setBigDecimal(2, BigDecimal.valueOf(10000));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private long insertAuditLog(long paymentOrderId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO payment_audit_logs (
                    payment_order_id,
                    event_type,
                    from_status,
                    to_status,
                    source,
                    reason_code,
                    actor_type,
                    occurred_at
                ) VALUES (?, 'PAYMENT_EXPIRED', 'PENDING', 'EXPIRED',
                    'EXPIRATION', 'TEST', 'SYSTEM', ?)
                RETURNING id
                """)) {
            statement.setLong(1, paymentOrderId);
            statement.setObject(2, OffsetDateTime.now());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean indexExists(String indexName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private long insertEventWithSoldQuantity(int soldQuantity) throws Exception {
        long organizationId = insertOrganization();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO events (
                    organizer_organization_id,
                    name,
                    event_type,
                    description,
                    venue_name,
                    address,
                    start_at,
                    end_at,
                    ticket_price,
                    ticket_total_quantity,
                    ticket_sold_quantity,
                    ticket_purchase_limit,
                    status,
                    booth_recruitment_enabled,
                    venue_map_enabled,
                    booth_reservation_enabled,
                    no_show_grace_minutes,
                    created_at,
                    updated_at
                ) VALUES (?, ?, 'EXPO', 'description', 'venue', 'address',
                    now(), now() + interval '1 day', 10000, 100, ?, 5,
                    'PUBLISHED', false, false, false, 0, now(), now())
                RETURNING id
                """)) {
            statement.setLong(1, organizationId);
            statement.setString(2, "inventory-event-" + System.nanoTime());
            statement.setInt(3, soldQuantity);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private long insertOrganization() throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO organizations (
                    organization_type,
                    name,
                    contact_email,
                    contact_phone,
                    status,
                    created_at,
                    updated_at
                ) VALUES ('COMPANY', ?, ?, '010-1234-5678', 'ACTIVE', now(), now())
                RETURNING id
                """)) {
            String suffix = Long.toString(System.nanoTime());
            statement.setString(1, "inventory-org-" + suffix);
            statement.setString(2, "inventory-" + suffix + "@example.com");
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private int ticketSoldQuantity(long eventId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ticket_sold_quantity FROM events WHERE id = ?")) {
            statement.setLong(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
