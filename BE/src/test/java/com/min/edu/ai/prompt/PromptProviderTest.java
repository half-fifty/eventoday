package com.min.edu.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;

class PromptProviderTest {

    private final PromptProvider promptProvider = new PromptProvider();

    @Test
    void loadsCopilotSystemPromptFromResource() {
        String prompt = promptProvider.get(PromptType.AI_COPILOT);

        assertThat(prompt).contains("Eventoday AI Copilot");
        assertThat(prompt).contains("payment keys");
        assertThat(prompt).contains("Respond only as valid JSON");
    }

    @Test
    void failsWhenPromptTypeIsNull() {
        assertThatThrownBy(() -> promptProvider.get(null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.AI_RESPONSE_INVALID);
    }
}
