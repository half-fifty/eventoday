package com.min.edu.ai.tool;

import com.min.edu.admission.service.AdmissionTicketOperationQueryService;
import com.min.edu.ai.dto.AdmissionTicketAiContext;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdmissionTicketAiTool
        implements AiTool<AdmissionTicketAiTool.Input, AdmissionTicketAiContext> {

    private static final String TOOL_NAME = "getAdmissionTicketStatus";

    private final AdmissionTicketOperationQueryService admissionTicketOperationQueryService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public AdmissionTicketAiContext execute(Input input, AiToolContext context) {
        if (input == null || input.admissionTicketId() == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return AdmissionTicketAiContext.from(admissionTicketOperationQueryService.getAdmissionTicketStatus(
            context.eventId(),
            context.memberId(),
            input.admissionTicketId()
        ));
    }

    public record Input(Long admissionTicketId) {
    }
}
