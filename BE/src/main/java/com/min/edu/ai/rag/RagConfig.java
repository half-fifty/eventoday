package com.min.edu.ai.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean(name = "ragEmbeddingModel")
    @ConditionalOnProperty(prefix = "ai.rag", name = "enabled", havingValue = "true")
    public EmbeddingModel ragEmbeddingModel(RagProperties properties) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException("AI_RAG_API_KEY is required when ai.rag.enabled=true");
        }
        GoogleGenAiEmbeddingConnectionDetails connectionDetails =
            GoogleGenAiEmbeddingConnectionDetails.builder()
                .apiKey(properties.apiKey())
                .build();
        GoogleGenAiTextEmbeddingOptions options = GoogleGenAiTextEmbeddingOptions.builder()
            .model(properties.embeddingModel())
            .dimensions(properties.embeddingDimensions())
            .build();
        return new GoogleGenAiTextEmbeddingModel(connectionDetails, options);
    }

    @Bean(name = "policyVectorStore")
    @ConditionalOnProperty(prefix = "ai.rag", name = "enabled", havingValue = "true")
    public VectorStore policyVectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel ragEmbeddingModel,
            RagProperties properties) {
        return PgVectorStore.builder(jdbcTemplate, ragEmbeddingModel)
            .vectorTableName(properties.vectorTable())
            .dimensions(properties.embeddingDimensions())
            .distanceType(PgDistanceType.COSINE_DISTANCE)
            .indexType(PgIndexType.NONE)
            .initializeSchema(false)
            .vectorTableValidationsEnabled(true)
            .build();
    }
}
