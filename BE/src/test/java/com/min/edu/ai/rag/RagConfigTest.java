package com.min.edu.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class RagConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(RagConfig.class)
        .withBean(JdbcTemplate.class, () -> org.mockito.Mockito.mock(JdbcTemplate.class));

    @Test
    void doesNotCreateEmbeddingOrVectorStoreWhenRagDisabledAndKeyMissing() {
        contextRunner
            .withPropertyValues(
                "ai.rag.enabled=false",
                "ai.rag.api-key=",
                "ai.rag.embedding-model=gemini-embedding-2",
                "ai.rag.embedding-dimensions=768",
                "ai.rag.top-k=1",
                "ai.rag.copilot-top-k=3",
                "ai.rag.policy-location=classpath:ai/policies/",
                "ai.rag.vector-table=eventoday_policy_vector",
                "ai.rag.ingestion-enabled=false"
            )
            .run(context -> {
                assertThat(context).doesNotHaveBean(EmbeddingModel.class);
                assertThat(context).doesNotHaveBean(VectorStore.class);
            });
    }

    @Test
    void reportsConfigurationFailureWhenRagEnabledWithoutApiKey() {
        contextRunner
            .withPropertyValues(
                "ai.rag.enabled=true",
                "ai.rag.api-key=",
                "ai.rag.embedding-model=gemini-embedding-2",
                "ai.rag.embedding-dimensions=768",
                "ai.rag.top-k=1",
                "ai.rag.copilot-top-k=3",
                "ai.rag.policy-location=classpath:ai/policies/",
                "ai.rag.vector-table=eventoday_policy_vector",
                "ai.rag.ingestion-enabled=false"
            )
            .run(context -> assertThat(context).hasFailed());
    }
}
