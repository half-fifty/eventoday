package com.min.edu.admission.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.dto.AdmissionTicketDtos;
import com.min.edu.admission.service.AdmissionTicketQueryService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.exception.GlobalExceptionHandler;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class AdmissionTicketControllerTest {
    private AdmissionTicketQueryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(AdmissionTicketQueryService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AdmissionTicketController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(authenticationPrincipalResolver())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyAdmissionTickets_returnsOwnTicketsWithoutInternalFields() throws Exception {
        authenticate(10L);
        given(service.getMyAdmissionTickets(
            eq(AdmissionTicketStatus.ISSUED),
            any(AuthenticatedMemberDto.class),
            eq(1),
            eq(10)))
            .willReturn(new PageImpl<>(List.of(myListResponse()), PageRequest.of(1, 10), 1));

        mockMvc.perform(get("/members/me/admission-tickets")
                .param("status", "ISSUED")
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].admissionTicketId").value(11))
            .andExpect(jsonPath("$.data.content[0].eventId").value(1))
            .andExpect(jsonPath("$.data.content[0].status").value("ISSUED"))
            .andExpect(jsonPath("$.data.content[0].qrToken").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].exchangeCodeId").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].memberId").doesNotExist());

        verify(service).getMyAdmissionTickets(
            eq(AdmissionTicketStatus.ISSUED),
            any(AuthenticatedMemberDto.class),
            eq(1),
            eq(10)
        );
    }

    @Test
    void getAdmissionTicketDetail_returnsDetailWithoutQrToken() throws Exception {
        authenticate(10L);
        given(service.getMyAdmissionTicketDetail(eq(11L), any(AuthenticatedMemberDto.class)))
            .willReturn(detailResponse());

        mockMvc.perform(get("/members/me/admission-tickets/11"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.admissionTicketId").value(11))
            .andExpect(jsonPath("$.data.exchangeCodeStatus").value("REDEEMED"))
            .andExpect(jsonPath("$.data.admissionTicketStatus").value("ISSUED"))
            .andExpect(jsonPath("$.data.qrAvailable").value(true))
            .andExpect(jsonPath("$.data.qrToken").doesNotExist());
    }

    @Test
    void getAdmissionTicketQr_returnsPng() throws Exception {
        authenticate(10L);
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
        given(service.getMyAdmissionTicketQr(eq(11L), any(AuthenticatedMemberDto.class)))
            .willReturn(png);

        mockMvc.perform(get("/members/me/admission-tickets/11/qr"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
            .andExpect(content().bytes(png));
    }

    @Test
    void getEventAdmissionTickets_returnsListWithoutInternalFields() throws Exception {
        authenticate(10L);
        given(service.getEventAdmissionTickets(
            eq(1L),
            eq(AdmissionTicketStatus.USED),
            any(AuthenticatedMemberDto.class),
            eq(0),
            eq(20)))
            .willReturn(new PageImpl<>(List.of(eventListResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/events/1/admission-tickets")
                .param("status", "USED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].admissionTicketId").value(11))
            .andExpect(jsonPath("$.data.content[0].memberNickname").value("holder"))
            .andExpect(jsonPath("$.data.content[0].qrToken").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].memberId").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].memberEmail").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].exchangeCodeId").doesNotExist());
    }

    @Test
    void admissionTicketApis_doNotExposeV1Path() throws Exception {
        authenticate(10L);

        mockMvc.perform(get("/v1/members/me/admission-tickets"))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/members/me/admission-tickets/11"))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/members/me/admission-tickets/11/qr"))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/events/1/admission-tickets"))
            .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }

    @Test
    void getMyAdmissionTickets_returnsBadRequestWhenStatusIsInvalid() throws Exception {
        authenticate(10L);

        mockMvc.perform(get("/members/me/admission-tickets")
                .param("status", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()))
            .andExpect(jsonPath("$.message").value(containsString("status")))
            .andExpect(jsonPath("$.message").value(containsString("ISSUED")))
            .andExpect(jsonPath("$.message").value(containsString("USED")))
            .andExpect(jsonPath("$.message").value(containsString("CANCELLED")))
            .andExpect(jsonPath("$.message").value(containsString("EXPIRED")));

        verifyNoInteractions(service);
    }

    @Test
    void getEventAdmissionTickets_returnsBadRequestForInvalidPageOrSize() throws Exception {
        authenticate(10L);
        willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE))
            .given(service)
            .getEventAdmissionTickets(eq(1L), eq(null), any(), eq(-1), eq(20));

        mockMvc.perform(get("/events/1/admission-tickets").param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));

        willThrow(new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE))
            .given(service)
            .getEventAdmissionTickets(eq(1L), eq(null), any(), eq(0), eq(101));

        mockMvc.perform(get("/events/1/admission-tickets").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    private AdmissionTicketDtos.MyListResponse myListResponse() {
        return new AdmissionTicketDtos.MyListResponse(
            11L,
            1L,
            "event",
            AdmissionTicketStatus.ISSUED,
            OffsetDateTime.parse("2026-08-06T10:00:00+09:00"),
            null,
            null
        );
    }

    private AdmissionTicketDtos.DetailResponse detailResponse() {
        return new AdmissionTicketDtos.DetailResponse(
            11L,
            1L,
            "event",
            ExchangeCodeStatus.REDEEMED,
            AdmissionTicketStatus.ISSUED,
            OffsetDateTime.parse("2026-08-06T10:00:00+09:00"),
            null,
            null,
            true
        );
    }

    private AdmissionTicketDtos.EventListResponse eventListResponse() {
        return new AdmissionTicketDtos.EventListResponse(
            11L,
            1L,
            "event",
            "holder",
            AdmissionTicketStatus.USED,
            OffsetDateTime.parse("2026-08-06T10:00:00+09:00"),
            OffsetDateTime.parse("2026-08-06T11:00:00+09:00"),
            null
        );
    }

    private void authenticate(Long memberId) {
        AuthenticatedMemberDto principal =
            new AuthenticatedMemberDto(memberId, PlatformRole.USER);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    return null;
                }
                return SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            }
        };
    }
}
