package com.min.edu.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public record AiCopilotRequest(
        @NotBlank
        @Size(max = 2000)
        String question,

        @Size(max = 100)
        String orderNo,

        @Valid
        Context context) {

    public AiCopilotRequest(String question, String orderNo) {
        this(question, orderNo, orderNo == null ? null : new Context(orderNo, null, null, null));
    }

    public record Context(
            @Size(max = 100) String orderNo,
            Long admissionTicketId,
            Long exchangeCodeId,
            @Size(max = 200) String qrToken) {
    }
}
