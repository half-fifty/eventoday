package com.min.edu.ai.service;

import com.min.edu.admission.service.AdmissionEligibilityQueryService;
import com.min.edu.ai.client.AiModelGateway;
import com.min.edu.ai.dto.AiChatRequest;
import com.min.edu.ai.dto.AiCopilotRequest;
import com.min.edu.ai.dto.AiCopilotResponse;
import com.min.edu.ai.prompt.PromptProvider;
import com.min.edu.ai.prompt.PromptType;
import com.min.edu.ai.rag.PolicyRetrievalResult;
import com.min.edu.ai.rag.PolicyRetrievalService;
import com.min.edu.ai.tool.AdmissionEligibilityAiTool;
import com.min.edu.ai.tool.AdmissionTicketAiTool;
import com.min.edu.ai.tool.AiTool;
import com.min.edu.ai.tool.AiToolContext;
import com.min.edu.ai.tool.EventOperationInfoAiTool;
import com.min.edu.ai.tool.ExchangeCodeAiTool;
import com.min.edu.ai.tool.TicketOrderAiTool;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.service.EventOperationAccessService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCopilotService {

    private static final int MAX_QUESTION_LENGTH = 2000;

    private final PromptProvider promptProvider;
    private final AiModelGateway aiModelGateway;
    private final PolicyRetrievalService policyRetrievalService;
    private final CopilotRetrievalQuerySanitizer retrievalQuerySanitizer;
    private final EventOperationAccessService eventOperationAccessService;
    private final AdmissionEligibilityQueryService admissionEligibilityQueryService;
    private final TicketOrderAiTool ticketOrderAiTool;
    private final AdmissionTicketAiTool admissionTicketAiTool;
    private final AdmissionEligibilityAiTool admissionEligibilityAiTool;
    private final ExchangeCodeAiTool exchangeCodeAiTool;
    private final EventOperationInfoAiTool eventOperationInfoAiTool;

    public AiCopilotResponse ask(Long eventId, AiCopilotRequest request, AuthenticatedMemberDto actor) {
        validate(eventId, request);
        eventOperationAccessService.requireOperationalAccess(eventId, actor);

        AiCopilotRequest.Context requestContext = normalizeContext(eventId, request);
        String requestId = UUID.randomUUID().toString();
        AiToolContext toolContext = AiToolContext.forEventOperation(
            actor,
            requestId,
            eventId
        );

        String safeRetrievalQuery = retrievalQuerySanitizer.buildRetrievalQuery(
            request.question(),
            requestContext
        );
        PolicyRetrievalResult policyContext = retrievePolicyContext(
            safeRetrievalQuery,
            requestId,
            eventId
        );

        String systemPrompt = promptProvider.get(PromptType.AI_COPILOT);
        String userPrompt = buildUserPrompt(
            retrievalQuerySanitizer.safeQuestionForPrompt(request.question()),
            requestContext,
            policyContext
        );
        return aiModelGateway.chat(new AiChatRequest(
            systemPrompt,
            userPrompt,
            operationTools(),
            toolContext
        )).response();
    }

    private void validate(Long eventId, AiCopilotRequest request) {
        if (eventId == null
                || request == null
                || !StringUtils.hasText(request.question())
                || request.question().length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private AiCopilotRequest.Context normalizeContext(
            Long eventId,
            AiCopilotRequest request) {
        AiCopilotRequest.Context context = request.context();
        if (context == null) {
            return StringUtils.hasText(request.orderNo())
                ? new AiCopilotRequest.Context(normalize(request.orderNo()), null, null, null)
                : null;
        }
        Long admissionTicketId = context.admissionTicketId();
        boolean qrProvided = StringUtils.hasText(context.qrToken());
        if (qrProvided) {
            admissionTicketId = admissionEligibilityQueryService.resolveTicketIdByQrToken(
                eventId,
                context.qrToken()
            );
        }
        return new AiCopilotRequest.Context(
            normalize(context.orderNo()),
            admissionTicketId,
            context.exchangeCodeId(),
            qrProvided ? "QR_PROVIDED" : null
        );
    }

    private PolicyRetrievalResult retrievePolicyContext(
            String safeRetrievalQuery,
            String requestId,
            Long eventId) {
        try {
            PolicyRetrievalResult result =
                policyRetrievalService.retrieveForCopilot(safeRetrievalQuery);
            log.info("AI copilot policy retrieval prepared. requestId={}, eventId={}, ragUsed={}, retrievedDocumentCount={}",
                requestId, eventId, result.used(), result.retrievedCount());
            return result;
        } catch (RuntimeException exception) {
            log.warn("AI copilot policy retrieval degraded. requestId={}, eventId={}, failureCategory={}",
                requestId, eventId, exception.getClass().getSimpleName());
            return PolicyRetrievalResult.empty();
        }
    }

    private String buildUserPrompt(
            String question,
            AiCopilotRequest.Context context,
            PolicyRetrievalResult policyContext) {
        return """
            [INFORMATION_PRIORITY]
            1. JAVA_BACKEND_DECISION
            2. TOOL_LIVE_RESULT
            3. TRUSTED_POLICY_CONTEXT
            4. USER_QUESTION
            [/INFORMATION_PRIORITY]

            [TRUSTED_POLICY_CONTEXT]
            %s
            [/TRUSTED_POLICY_CONTEXT]

            User question:
            %s

            Server-provided operation context. Use these identifiers when calling tools.
            Never ask for or reveal QR tokens, access tokens, payment keys, emails, or phone numbers.

            orderNo: %s
            admissionTicketId: %s
            exchangeCodeId: %s
            qrProvided: %s

            Respond only with valid JSON matching:
            {"answer":"Korean answer shown to the user","category":"PAYMENT|TICKET_ORDER|REFUND|ADMISSION|EVENT|GENERAL","needsHumanSupport":false}
            """.formatted(
            policyContext == null ? "NONE" : policyContext.context(),
            question.trim(),
            context == null ? null : context.orderNo(),
            context == null ? null : context.admissionTicketId(),
            context == null ? null : context.exchangeCodeId(),
            context != null && "QR_PROVIDED".equals(context.qrToken())
        );
    }

    private List<AiTool<?, ?>> operationTools() {
        return List.of(
            ticketOrderAiTool,
            admissionTicketAiTool,
            admissionEligibilityAiTool,
            exchangeCodeAiTool,
            eventOperationInfoAiTool
        );
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
