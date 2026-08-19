package com.min.edu.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.min.edu.ai.client.AiModelGateway;
import com.min.edu.ai.dto.AiChatRequest;
import com.min.edu.ai.dto.AiFailureExplanationOutput;
import com.min.edu.ai.dto.AiFailureExplanationRequest;
import com.min.edu.ai.dto.AiFailureExplanationResponse;
import com.min.edu.ai.dto.RefundFailureExplanationContext;
import com.min.edu.ai.prompt.PromptProvider;
import com.min.edu.ai.prompt.PromptType;
import com.min.edu.ai.rag.PolicyRetrievalRequest;
import com.min.edu.ai.rag.PolicyRetrievalResult;
import com.min.edu.ai.rag.PolicyRetrievalService;
import com.min.edu.ai.rag.PolicyType;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.policy.RefundEligibilityReasonCode;
import com.min.edu.payment.service.RefundEligibilityQueryService;
import com.min.edu.payment.service.RefundEligibilityView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiRefundFailureExplanationService {

    private static final int MAX_QUESTION_LENGTH = 1000;

    private final RefundEligibilityQueryService refundEligibilityQueryService;
    private final PromptProvider promptProvider;
    private final AiModelGateway aiModelGateway;
    private final PolicyRetrievalService policyRetrievalService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public AiFailureExplanationResponse explain(
            Long memberId,
            String orderAccessToken,
            Long paymentId,
            AiFailureExplanationRequest request) {
        validate(paymentId, request);
        RefundEligibilityView view = refundEligibilityQueryService.evaluate(
            memberId,
            orderAccessToken,
            paymentId
        );
        RefundFailureExplanationContext context =
            RefundFailureExplanationContext.from(view, fallbackExplanation(view));
        PolicyRetrievalResult policyContext = policyRetrievalService.retrieve(
            new PolicyRetrievalRequest(PolicyType.REFUND, context.reasonCode())
        );
        try {
            AiFailureExplanationOutput output = aiModelGateway.chat(new AiChatRequest(
                promptProvider.get(PromptType.REFUND_FAILURE_EXPLANATION),
                buildUserPrompt(request.question(), context, policyContext)
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

    private void validate(Long paymentId, AiFailureExplanationRequest request) {
        if (paymentId == null
                || request == null
                || !StringUtils.hasText(request.question())
                || request.question().length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String buildUserPrompt(
            String question,
            RefundFailureExplanationContext context,
            PolicyRetrievalResult policyContext) {
        return """
            [BACKEND_DECISION]
            refundable: %s
            reasonCode: %s

            [CURRENT_STATE]
            %s

            [POLICY_CONTEXT]
            %s

            [USER_QUESTION]
            %s
            """.formatted(
            context.refundable(),
            context.reasonCode(),
            toJson(context),
            policyContext == null ? "NONE" : policyContext.context(),
            question.trim()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }

    private AiFailureExplanationResponse fallback(RefundFailureExplanationContext context) {
        return new AiFailureExplanationResponse(
            context.fallbackMessage(),
            fallbackRecommendedAction(context),
            true,
            false,
            context.reasonCode()
        );
    }

    private String fallbackExplanation(RefundEligibilityView view) {
        RefundEligibilityReasonCode reasonCode = view.eligibility().reasonCode();
        return switch (reasonCode) {
            case ELIGIBLE -> "현재 조회 기준으로는 환불 제한 사유가 확인되지 않습니다.";
            case PAYMENT_NOT_PAID -> "결제가 완료된 상태가 아니어서 현재 환불을 진행할 수 없습니다.";
            case PAYMENT_ORDER_NOT_PAID -> "주문 결제 상태가 완료 상태가 아니어서 현재 환불을 진행할 수 없습니다.";
            case TICKET_ORDER_NOT_CONFIRMED -> "티켓 주문이 확정된 상태가 아니어서 현재 환불을 진행할 수 없습니다.";
            case REFUND_AMOUNT_NOT_POSITIVE -> "환불 가능한 결제 금액이 없어 현재 환불을 진행할 수 없습니다.";
            case OPERATION_CUTOFF_PASSED -> "행사 운영 마감 시각이 지나 현재 환불이 제한됩니다.";
            case EXCHANGE_CODE_ALREADY_REDEEMED -> "교환 코드가 이미 사용된 상태라 현재 환불이 제한됩니다.";
        };
    }

    private String fallbackRecommendedAction(RefundFailureExplanationContext context) {
        if ("ELIGIBLE".equals(context.reasonCode())) {
            return "방금 실패한 요청이 있다면 잠시 후 다시 시도하거나 행사 운영자에게 문의해 주세요.";
        }
        return "상태가 잘못 표시된 것으로 보이면 행사 운영자에게 문의해 주세요.";
    }

    private boolean isAiProviderFailure(BusinessException exception) {
        GlobalErrorCode errorCode = exception.getErrorCode();
        return errorCode == GlobalErrorCode.AI_SERVICE_UNAVAILABLE
            || errorCode == GlobalErrorCode.AI_REQUEST_TIMEOUT
            || errorCode == GlobalErrorCode.AI_RESPONSE_INVALID;
    }
}
