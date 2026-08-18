package com.min.edu.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.min.edu.admission.policy.AdmissionEligibilityReasonCode;
import com.min.edu.admission.service.AdmissionFailureEligibilityQueryService;
import com.min.edu.admission.service.AdmissionFailureEligibilityView;
import com.min.edu.ai.client.AiModelGateway;
import com.min.edu.ai.dto.AdmissionFailureExplanationContext;
import com.min.edu.ai.dto.AiChatRequest;
import com.min.edu.ai.dto.AiFailureExplanationOutput;
import com.min.edu.ai.dto.AiFailureExplanationRequest;
import com.min.edu.ai.dto.AiFailureExplanationResponse;
import com.min.edu.ai.prompt.PromptProvider;
import com.min.edu.ai.prompt.PromptType;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiAdmissionFailureExplanationService {

    private static final int MAX_QUESTION_LENGTH = 1000;

    private final AdmissionFailureEligibilityQueryService admissionFailureEligibilityQueryService;
    private final PromptProvider promptProvider;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public AiFailureExplanationResponse explainForMember(
            Long memberId,
            Long admissionTicketId,
            AiFailureExplanationRequest request) {
        validate(admissionTicketId, request);
        AdmissionFailureEligibilityView view =
            admissionFailureEligibilityQueryService.evaluateForMember(memberId, admissionTicketId);
        return explain(request, view);
    }

    public AiFailureExplanationResponse explainForGuest(
            String orderNo,
            String orderAccessToken,
            Long admissionTicketId,
            AiFailureExplanationRequest request) {
        validate(admissionTicketId, request);
        AdmissionFailureEligibilityView view =
            admissionFailureEligibilityQueryService.evaluateForGuest(
                orderNo,
                orderAccessToken,
                admissionTicketId
            );
        return explain(request, view);
    }

    private AiFailureExplanationResponse explain(
            AiFailureExplanationRequest request,
            AdmissionFailureEligibilityView view) {
        AdmissionFailureExplanationContext context =
            AdmissionFailureExplanationContext.from(view, fallbackExplanation(view));
        try {
            AiFailureExplanationOutput output = aiModelGateway.chat(new AiChatRequest(
                promptProvider.get(PromptType.ADMISSION_FAILURE_EXPLANATION),
                buildUserPrompt(request.question(), context)
            ), AiFailureExplanationOutput.class);
            return new AiFailureExplanationResponse(
                output.explanation(),
                output.recommendedAction(),
                output.needsHumanSupport(),
                true,
                context.reasonCode()
            );
        } catch (BusinessException exception) {
            if (!isAiProviderFailure(exception)) {
                throw exception;
            }
            return fallback(context);
        }
    }

    private void validate(Long admissionTicketId, AiFailureExplanationRequest request) {
        if (admissionTicketId == null
                || request == null
                || !StringUtils.hasText(request.question())
                || request.question().length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String buildUserPrompt(
            String question,
            AdmissionFailureExplanationContext context) {
        return """
            User question:
            %s

            Server-confirmed admission eligibility context. Explain this context only.
            %s
            """.formatted(question.trim(), toJson(context));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }

    private AiFailureExplanationResponse fallback(AdmissionFailureExplanationContext context) {
        return new AiFailureExplanationResponse(
            context.fallbackMessage(),
            fallbackRecommendedAction(context),
            true,
            false,
            context.reasonCode()
        );
    }

    private String fallbackExplanation(AdmissionFailureEligibilityView view) {
        AdmissionEligibilityReasonCode reasonCode = view.eligibility().reasonCode();
        return switch (reasonCode) {
            case ELIGIBLE -> "현재 조회 기준으로는 입장 제한 사유가 확인되지 않습니다.";
            case EVENT_MISMATCH -> "해당 입장권은 요청한 행사와 일치하지 않아 입장이 제한됩니다.";
            case EVENT_NOT_PUBLISHED -> "행사가 현재 공개 상태가 아니어서 입장이 제한됩니다.";
            case EVENT_ENDED -> "행사가 종료되어 현재 입장이 제한됩니다.";
            case ALREADY_USED -> "해당 입장권은 이미 사용된 상태라 중복 입장이 제한됩니다.";
            case TICKET_NOT_ISSUED -> "입장권이 발급 완료 상태가 아니어서 현재 입장이 제한됩니다.";
        };
    }

    private String fallbackRecommendedAction(AdmissionFailureExplanationContext context) {
        if ("ELIGIBLE".equals(context.reasonCode())) {
            return "방금 실패한 입장 시도가 있다면 현장 staff에게 현재 상태를 확인해 주세요.";
        }
        return "상태가 잘못 표시된 것으로 보이면 현장 staff 또는 행사 운영자에게 문의해 주세요.";
    }

    private boolean isAiProviderFailure(BusinessException exception) {
        GlobalErrorCode errorCode = exception.getErrorCode();
        return errorCode == GlobalErrorCode.AI_SERVICE_UNAVAILABLE
            || errorCode == GlobalErrorCode.AI_REQUEST_TIMEOUT
            || errorCode == GlobalErrorCode.AI_RESPONSE_INVALID;
    }
}
