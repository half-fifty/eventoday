package com.min.edu.admission.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import com.min.edu.admission.dto.ExchangeCodeRequestDtos;
import com.min.edu.admission.service.ExchangeCodeIssuanceService;
import com.min.edu.admission.service.ExchangeCodeRequestService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.exception.GlobalExceptionHandler;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ExchangeCodeRequestControllerTest {

    private ExchangeCodeRequestService service;
    private ExchangeCodeIssuanceService issuanceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(ExchangeCodeRequestService.class);
        issuanceService = org.mockito.Mockito.mock(ExchangeCodeIssuanceService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ExchangeCodeRequestController(service, issuanceService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(
                authenticationPrincipalResolver(),
                new PageableHandlerMethodArgumentResolver()
            )
            .build();
    }

    @Test
    void createRequest_returnsCreatedRequest() throws Exception {
        given(service.createRequest(
            eq(1L),
            any(ExchangeCodeRequestDtos.CreateRequest.class),
            any(AuthenticatedMemberDto.class)))
            .willReturn(new ExchangeCodeRequestDtos.CreateResponse(
                7L,
                1L,
                10L,
                3,
                "external sales",
                ExchangeCodeRequestStatus.REQUESTED,
                OffsetDateTime.parse("2026-08-06T10:00:00+09:00")
            ));

        mockMvc.perform(post("/events/1/exchange-code-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requestedQuantity": 3,
                      "purpose": "external sales"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.requestId").value(7))
            .andExpect(jsonPath("$.data.status").value("REQUESTED"));

        verify(service).createRequest(
            eq(1L),
            any(ExchangeCodeRequestDtos.CreateRequest.class),
            any(AuthenticatedMemberDto.class)
        );
    }

    @Test
    void createRequest_returnsBadRequestWhenQuantityIsMissingOrOutOfRange() throws Exception {
        mockMvc.perform(post("/events/1/exchange-code-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "purpose": "external sales"
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/events/1/exchange-code-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requestedQuantity": 0,
                      "purpose": "external sales"
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/events/1/exchange-code-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requestedQuantity": 1001,
                      "purpose": "external sales"
                    }
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void createRequest_returnsBadRequestWhenPurposeIsMissingBlankOrTooLong() throws Exception {
        mockMvc.perform(post("/events/1/exchange-code-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requestedQuantity": 1
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/events/1/exchange-code-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requestedQuantity": 1,
                      "purpose": " "
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/events/1/exchange-code-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requestedQuantity": 1,
                      "purpose": "%s"
                    }
                    """.formatted("a".repeat(501))))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void getEventRequests_passesStatusAndPageable() throws Exception {
        given(service.getEventRequests(
            eq(1L),
            eq(ExchangeCodeRequestStatus.APPROVED),
            any(AuthenticatedMemberDto.class),
            any(Pageable.class)))
            .willReturn(Page.empty(org.springframework.data.domain.PageRequest.of(1, 10)));

        mockMvc.perform(get("/events/1/exchange-code-requests")
                .param("status", "APPROVED")
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"));

        verify(service).getEventRequests(
            eq(1L),
            eq(ExchangeCodeRequestStatus.APPROVED),
            any(AuthenticatedMemberDto.class),
            any(Pageable.class)
        );
    }

    @Test
    void getEventRequests_returnsBadRequestWhenStatusIsInvalid() throws Exception {
        mockMvc.perform(get("/events/1/exchange-code-requests")
                .param("status", "FOO"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()))
            .andExpect(jsonPath("$.message").value(containsString("status")))
            .andExpect(jsonPath("$.message").value(containsString("REQUESTED")))
            .andExpect(jsonPath("$.message").value(containsString("APPROVED")))
            .andExpect(jsonPath("$.message").value(containsString("REJECTED")))
            .andExpect(jsonPath("$.message").value(containsString("ISSUED")));

        verifyNoInteractions(service);
    }

    @Test
    void getRequestDetail_returnsDetail() throws Exception {
        given(service.getRequestDetail(eq(7L), any(AuthenticatedMemberDto.class)))
            .willReturn(response(7L, ExchangeCodeRequestStatus.REQUESTED));

        mockMvc.perform(get("/exchange-code-requests/7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requestId").value(7));
    }

    @Test
    void getAdminRequests_passesStatusAndPageable() throws Exception {
        given(service.getAdminRequests(
            eq(ExchangeCodeRequestStatus.REQUESTED),
            any(AuthenticatedMemberDto.class),
            any(Pageable.class)))
            .willReturn(Page.empty(org.springframework.data.domain.PageRequest.of(0, 20)));

        mockMvc.perform(get("/admin/exchange-code-requests")
                .param("status", "REQUESTED")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk());

        verify(service).getAdminRequests(
            eq(ExchangeCodeRequestStatus.REQUESTED),
            any(AuthenticatedMemberDto.class),
            any(Pageable.class)
        );
    }

    @Test
    void getAdminRequests_returnsBadRequestWhenStatusIsInvalid() throws Exception {
        mockMvc.perform(get("/admin/exchange-code-requests")
                .param("status", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()))
            .andExpect(jsonPath("$.message").value(containsString("REQUESTED")))
            .andExpect(jsonPath("$.message").value(containsString("APPROVED")))
            .andExpect(jsonPath("$.message").value(containsString("REJECTED")))
            .andExpect(jsonPath("$.message").value(containsString("ISSUED")));

        verifyNoInteractions(service);
    }

    @Test
    void approveRequest_delegatesToService() throws Exception {
        mockMvc.perform(post("/admin/exchange-code-requests/7/approval"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"));

        verify(service).approveRequest(eq(7L), any(AuthenticatedMemberDto.class));
    }

    @Test
    void rejectRequest_validatesReason() throws Exception {
        mockMvc.perform(post("/admin/exchange-code-requests/7/rejection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/admin/exchange-code-requests/7/rejection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": " "
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/admin/exchange-code-requests/7/rejection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "%s"
                    }
                    """.formatted("a".repeat(2001))))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rejectRequest_delegatesToService() throws Exception {
        mockMvc.perform(post("/admin/exchange-code-requests/7/rejection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "not enough detail"
                    }
                    """))
            .andExpect(status().isOk());

        verify(service).rejectRequest(
            eq(7L),
            eq("not enough detail"),
            any(AuthenticatedMemberDto.class)
        );
    }

    @Test
    void issueRequest_delegatesToServiceWithoutBody() throws Exception {
        given(issuanceService.issue(eq(7L), any(AuthenticatedMemberDto.class)))
            .willReturn(new ExchangeCodeRequestDtos.IssuanceResponse(
                7L,
                1L,
                ExchangeCodeRequestStatus.ISSUED,
                3,
                3,
                OffsetDateTime.parse("2026-08-06T10:00:00+09:00")
            ));

        mockMvc.perform(post("/admin/exchange-code-requests/7/issuance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.requestId").value(7))
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
            .andExpect(jsonPath("$.data.generatedQuantity").value(3))
            .andExpect(jsonPath("$.data.codes").doesNotExist())
            .andExpect(jsonPath("$.data.recipientEmail").doesNotExist());

        verify(issuanceService).issue(eq(7L), any(AuthenticatedMemberDto.class));
    }

    @Test
    void issueRequest_returnsConflictWhenAlreadyIssued() throws Exception {
        willThrow(new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ALREADY_ISSUED))
            .given(issuanceService)
            .issue(eq(7L), any(AuthenticatedMemberDto.class));

        mockMvc.perform(post("/admin/exchange-code-requests/7/issuance"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ALREADY_ISSUED.getCode()));
    }

    @Test
    void resendIssueEmail_delegatesToServiceWithoutBody() throws Exception {
        given(issuanceService.resendEmail(eq(7L), any(AuthenticatedMemberDto.class)))
            .willReturn(new ExchangeCodeRequestDtos.EmailResendResponse(
                7L,
                1L,
                ExchangeCodeRequestStatus.ISSUED,
                3,
                3,
                OffsetDateTime.parse("2026-08-06T10:00:00+09:00")
            ));

        mockMvc.perform(post("/admin/exchange-code-requests/7/email-resend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.data.requestId").value(7))
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
            .andExpect(jsonPath("$.data.codeCount").value(3))
            .andExpect(jsonPath("$.data.codes").doesNotExist())
            .andExpect(jsonPath("$.data.recipientEmail").doesNotExist());

        verify(issuanceService).resendEmail(eq(7L), any(AuthenticatedMemberDto.class));
    }

    @Test
    void resendIssueEmail_returnsUnauthorized() throws Exception {
        willThrow(new BusinessException(GlobalErrorCode.UNAUTHORIZED))
            .given(issuanceService)
            .resendEmail(eq(7L), any(AuthenticatedMemberDto.class));

        mockMvc.perform(post("/admin/exchange-code-requests/7/email-resend"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void resendIssueEmail_returnsForbidden() throws Exception {
        willThrow(new BusinessException(GlobalErrorCode.FORBIDDEN))
            .given(issuanceService)
            .resendEmail(eq(7L), any(AuthenticatedMemberDto.class));

        mockMvc.perform(post("/admin/exchange-code-requests/7/email-resend"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void resendIssueEmail_returnsConflictWhenAlreadySent() throws Exception {
        willThrow(new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EMAIL_ALREADY_SENT))
            .given(issuanceService)
            .resendEmail(eq(7L), any(AuthenticatedMemberDto.class));

        mockMvc.perform(post("/admin/exchange-code-requests/7/email-resend"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EMAIL_ALREADY_SENT.getCode()));
    }

    private ExchangeCodeRequestDtos.Response response(
            Long requestId,
            ExchangeCodeRequestStatus status) {
        return new ExchangeCodeRequestDtos.Response(
            requestId,
            1L,
            "event",
            10L,
            "requester",
            3,
            "purpose",
            status,
            null,
            null,
            null,
            null,
            OffsetDateTime.parse("2026-08-06T10:00:00+09:00")
        );
    }

    private HandlerMethodArgumentResolver authenticationPrincipalResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory) {
                return new AuthenticatedMemberDto(10L, PlatformRole.USER);
            }
        };
    }
}
