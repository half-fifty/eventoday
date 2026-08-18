package com.min.edu.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

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
}
