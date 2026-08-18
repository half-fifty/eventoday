package com.min.edu.ai.tool;

import com.min.edu.admission.service.AdmissionEligibilityQueryService;
import com.min.edu.ai.dto.AdmissionEligibilityAiContext;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.service.EventOperationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdmissionEligibilityAiTool
        implements AiTool<AdmissionEligibilityAiTool.Input, AdmissionEligibilityAiContext> {

    private static final String TOOL_NAME = "getAdmissionEligibility";

    private final AdmissionEligibilityQueryService admissionEligibilityQueryService;
    private final EventOperationAccessService eventOperationAccessService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public AdmissionEligibilityAiContext execute(Input input, AiToolContext context) {
        eventOperationAccessService.requireOperationalAccess(context.eventId(), context.memberId());
        if (input == null || input.admissionTicketId() == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return AdmissionEligibilityAiContext.from(
            admissionEligibilityQueryService.evaluateByTicketId(
                context.eventId(),
                input.admissionTicketId()
            )
        );
    }

    public record Input(Long admissionTicketId) {
    }
}
