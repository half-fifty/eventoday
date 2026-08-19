package com.min.edu.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.min.edu.ai.rag.RagProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class AiPropertiesTest {

    @Test
    void bindsCopilotPropertiesFromDedicatedNamespace() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("ai.copilot.provider", "google")
            .withProperty("ai.copilot.api-key", "copilot-key")
            .withProperty("ai.copilot.model", "gemini-3.5-flash-lite")
            .withProperty("ai.copilot.connect-timeout", "4s")
            .withProperty("ai.copilot.read-timeout", "12s")
            .withProperty("ai.copilot.max-output-tokens", "900")
            .withProperty("external.openai.api-key", "booth-review-key");

        AiProperties properties = Binder.get(environment)
            .bind("ai.copilot", Bindable.of(AiProperties.class))
            .get();

        assertThat(properties.provider()).isEqualTo("google");
        assertThat(properties.apiKey()).isEqualTo("copilot-key");
        assertThat(properties.model()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(4));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(12));
        assertThat(properties.maxOutputTokens()).isEqualTo(900);
    }

    @Test
    void bindsRagPropertiesFromDedicatedNamespace() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("ai.rag.enabled", "true")
            .withProperty("ai.rag.api-key", "rag-key")
            .withProperty("ai.rag.embedding-model", "gemini-embedding-2")
            .withProperty("ai.rag.embedding-dimensions", "768")
            .withProperty("ai.rag.top-k", "1")
            .withProperty("ai.rag.policy-location", "classpath:ai/policies/")
            .withProperty("ai.rag.vector-table", "eventoday_policy_vector")
            .withProperty("ai.rag.ingestion-enabled", "false")
            .withProperty("ai.copilot.api-key", "copilot-key");

        RagProperties properties = Binder.get(environment)
            .bind("ai.rag", Bindable.of(RagProperties.class))
            .get();

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.apiKey()).isEqualTo("rag-key");
        assertThat(properties.embeddingModel()).isEqualTo("gemini-embedding-2");
        assertThat(properties.embeddingDimensions()).isEqualTo(768);
        assertThat(properties.topK()).isEqualTo(1);
        assertThat(properties.vectorTable()).isEqualTo("eventoday_policy_vector");
        assertThat(properties.ingestionEnabled()).isFalse();
    }
}
