package com.min.edu.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PgVectorPolicyStoreIntegrationTest {

    private static final int DIMENSIONS = 768;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres")
    );

    @Test
    void flywayCreatesPgvectorSchemaAndVectorStoreSearchesWithMetadataFilters() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .outOfOrder(true)
            .load()
            .migrate();

        Integer vectorExtensionCount = jdbcTemplate.queryForObject(
            "select count(*) from pg_extension where extname = 'vector'",
            Integer.class
        );
        Integer tableCount = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'eventoday_policy_vector'",
            Integer.class
        );
        assertThat(vectorExtensionCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(1);

        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, new FakeEmbeddingModel(DIMENSIONS))
            .vectorTableName("eventoday_policy_vector")
            .dimensions(DIMENSIONS)
            .indexType(PgIndexType.NONE)
            .initializeSchema(false)
            .vectorTableValidationsEnabled(true)
            .build();

        Document refund = document("11111111-1111-1111-1111-111111111111", "refund policy",
            PolicyType.REFUND, "EXCHANGE_CODE_ALREADY_REDEEMED", "refund-policy.md");
        Document admission = document("22222222-2222-2222-2222-222222222222", "admission policy",
            PolicyType.ADMISSION, "ALREADY_USED", "admission-policy.md");
        vectorStore.add(List.of(refund, admission));

        List<Document> refundResults = vectorStore.similaritySearch(search(
            PolicyType.REFUND,
            "EXCHANGE_CODE_ALREADY_REDEEMED",
            "REFUND EXCHANGE_CODE_ALREADY_REDEEMED policy"
        ));
        List<Document> admissionResults = vectorStore.similaritySearch(search(
            PolicyType.ADMISSION,
            "ALREADY_USED",
            "ADMISSION ALREADY_USED policy"
        ));

        assertThat(refundResults)
            .extracting(document -> document.getMetadata().get("policyType"))
            .containsOnly("REFUND");
        assertThat(admissionResults)
            .extracting(document -> document.getMetadata().get("policyType"))
            .containsOnly("ADMISSION");

        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        vectorStore.delete(builder.eq("policyType", PolicyType.REFUND.name()).build());
        assertThat(vectorStore.similaritySearch(search(
            PolicyType.REFUND,
            "EXCHANGE_CODE_ALREADY_REDEEMED",
            "REFUND EXCHANGE_CODE_ALREADY_REDEEMED policy"
        ))).isEmpty();
        assertThat(vectorStore.similaritySearch(search(
            PolicyType.ADMISSION,
            "ALREADY_USED",
            "ADMISSION ALREADY_USED policy"
        ))).hasSize(1);

        vectorStore.add(List.of(refund));
        assertThat(vectorStore.similaritySearch(search(
            PolicyType.REFUND,
            "EXCHANGE_CODE_ALREADY_REDEEMED",
            "REFUND EXCHANGE_CODE_ALREADY_REDEEMED policy"
        ))).hasSize(1);
    }

    private JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private Document document(
            String id,
            String content,
            PolicyType policyType,
            String reasonCode,
            String documentName) {
        return new Document(id, content, Map.of(
            "policyType", policyType.name(),
            "reasonCode", reasonCode,
            "section", reasonCode,
            "documentName", documentName,
            "version", 1
        ));
    }

    private SearchRequest search(PolicyType policyType, String reasonCode, String query) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return SearchRequest.builder()
            .query(query)
            .topK(5)
            .similarityThresholdAll()
            .filterExpression(builder.and(
                builder.eq("policyType", policyType.name()),
                builder.eq("reasonCode", reasonCode)
            ).build())
            .build();
    }

    private static class FakeEmbeddingModel implements EmbeddingModel {

        private final int dimensions;

        FakeEmbeddingModel(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new Embedding(vector(request.getInstructions().get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        private float[] vector(String text) {
            Random random = new Random(seed(text));
            float[] vector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                vector[i] = random.nextFloat();
            }
            return vector;
        }

        private long seed(String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            long seed = 1125899906842597L;
            for (byte value : bytes) {
                seed = 31 * seed + value;
            }
            return seed;
        }
    }
}
