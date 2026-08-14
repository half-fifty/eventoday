package com.min.edu.payment.toss;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TossConfirmResponseTest {

    @Test
    void deserializeVirtualAccountDueDateWithoutTimezoneOffset() throws Exception {
        TossConfirmResponse response = objectMapper().readValue(
            """
            {
              "paymentKey": "payment-key",
              "orderId": "ORDER-1",
              "totalAmount": 10000,
              "status": "WAITING_FOR_DEPOSIT",
              "method": "VIRTUAL_ACCOUNT",
              "secret": "secret-value",
              "virtualAccount": {
                "accountNumber": "1234567890",
                "bankCode": "088",
                "customerName": "tester",
                "dueDate": "2026-08-03T10:30:00"
              },
              "requestedAt": "2026-08-03T10:00:00+09:00"
            }
            """,
            TossConfirmResponse.class
        );

        assertThat(response.virtualAccount().dueDate())
            .isEqualTo(OffsetDateTime.parse("2026-08-03T10:30:00+09:00"));
    }

    @Test
    void deserializeRequestedAndApprovedAtWithFractionalSeconds() throws Exception {
        for (String datetime : new String[] {
            "2026-08-14T02:07:48+09:00",
            "2026-08-14T02:07:48.123+09:00",
            "2026-08-14T02:07:48.123456+09:00"
        }) {
            TossConfirmResponse response = objectMapper().readValue(
                """
                {
                  "paymentKey": "payment-key",
                  "orderId": "ORDER-1",
                  "totalAmount": 10000,
                  "status": "DONE",
                  "method": "CARD",
                  "requestedAt": "%s",
                  "approvedAt": "%s"
                }
                """.formatted(datetime, datetime),
                TossConfirmResponse.class
            );

            assertThat(response.requestedAt().toInstant())
                .isEqualTo(OffsetDateTime.parse(datetime).toInstant());
            assertThat(response.approvedAt().toInstant())
                .isEqualTo(OffsetDateTime.parse(datetime).toInstant());
        }
    }

    @Test
    void deserializeVirtualAccountDueDateWithFractionalSeconds() throws Exception {
        for (DueDateCase testCase : new DueDateCase[] {
            new DueDateCase(
                "2026-08-14T16:03:39+09:00",
                "2026-08-14T16:03:39+09:00"
            ),
            new DueDateCase(
                "2026-08-14T16:03:39",
                "2026-08-14T16:03:39+09:00"
            ),
            new DueDateCase(
                "2026-08-14T16:03:39.123+09:00",
                "2026-08-14T16:03:39.123+09:00"
            ),
            new DueDateCase(
                "2026-08-14T16:03:39.123",
                "2026-08-14T16:03:39.123+09:00"
            )
        }) {
            TossConfirmResponse response = objectMapper().readValue(
                """
                {
                  "paymentKey": "payment-key",
                  "orderId": "ORDER-1",
                  "totalAmount": 10000,
                  "status": "WAITING_FOR_DEPOSIT",
                  "method": "VIRTUAL_ACCOUNT",
                  "secret": "secret-value",
                  "virtualAccount": {
                    "accountNumber": "1234567890",
                    "bankCode": "088",
                    "customerName": "tester",
                    "dueDate": "%s"
                  },
                  "requestedAt": "2026-08-14T02:07:48+09:00"
                }
                """.formatted(testCase.input()),
                TossConfirmResponse.class
            );

            assertThat(response.virtualAccount().dueDate())
                .isEqualTo(OffsetDateTime.parse(testCase.expected()));
        }
    }

    @Test
    void toStringMasksVirtualAccountSensitiveFields() {
        TossConfirmResponse response = new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            java.math.BigDecimal.valueOf(10000),
            "WAITING_FOR_DEPOSIT",
            "VIRTUAL_ACCOUNT",
            "secret-value",
            new TossConfirmResponse.VirtualAccount(
                "1234567890",
                "088",
                "tester",
                OffsetDateTime.parse("2026-08-03T10:30:00+09:00")
            ),
            null,
            null
        );

        assertThat(response.toString())
            .doesNotContain("secret-value")
            .doesNotContain("1234567890")
            .doesNotContain("tester")
            .contains("secret=***")
            .contains("virtualAccount=[MASKED]");
        assertThat(response.virtualAccount().toString())
            .doesNotContain("1234567890")
            .doesNotContain("tester");
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private record DueDateCase(String input, String expected) {
    }
}
