package com.min.edu.ai.prompt;

public enum PromptType {
    AI_COPILOT("prompts/ai-copilot-system.md"),
    REFUND_FAILURE_EXPLANATION("prompts/refund-failure-explanation-system.md"),
    ADMISSION_FAILURE_EXPLANATION("prompts/admission-failure-explanation-system.md");

    private final String resourcePath;

    PromptType(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String resourcePath() {
        return resourcePath;
    }
}
