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
    void loadsFailureExplanationPromptsFromResources() {
        String refundPrompt = promptProvider.get(PromptType.REFUND_FAILURE_EXPLANATION);
        String admissionPrompt = promptProvider.get(PromptType.ADMISSION_FAILURE_EXPLANATION);

        assertThat(refundPrompt)
            .contains("refund failure")
            .contains("Never decide refund eligibility yourself")
            .doesNotContain("AI_COPILOT_API_KEY");
        assertThat(admissionPrompt)
            .contains("admission failure")
            .contains("Never decide admission eligibility yourself")
            .contains("EVENT_NOT_STARTED")
            .doesNotContain("AI_COPILOT_API_KEY");
    }

    @Test
    void failsWhenPromptTypeIsNull() {
        assertThatThrownBy(() -> promptProvider.get(null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.AI_RESPONSE_INVALID);
    }
}
