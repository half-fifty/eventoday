package com.min.edu.funnel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.GlobalExceptionHandler;
import com.min.edu.funnel.service.FunnelActionCollectorService;
import com.min.edu.funnel.support.AnonymousIdCookieFactory;
import com.min.edu.member.domain.PlatformRole;

class FunnelActionControllerTest {

    private static final String ISSUED_ANONYMOUS_ID = "11111111-1111-1111-1111-111111111111";

    private FunnelActionCollectorService funnelActionCollectorService;
    private AnonymousIdCookieFactory anonymousIdCookieFactory;
    private MockMvc mockMvc;
    private AuthenticatedMemberDto principal;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        funnelActionCollectorService = mock(FunnelActionCollectorService.class);
        anonymousIdCookieFactory = mock(AnonymousIdCookieFactory.class);
        principal = null;

        FunnelActionController controller = new FunnelActionController(
                funnelActionCollectorService,
                anonymousIdCookieFactory);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authenticationPrincipalResolver())
                .build();
    }

    private String requestBody() throws Exception {
        return objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {
            {
                put("sessionId", "b9d8a554-940f-4d72-b6de-711616158aad");
                put("eventId", 42L);
                put("actionType", "VIEW_EVENT_DETAIL");
                put("occurredAt", OffsetDateTime.now().toString());
                put("properties", java.util.Map.of());
            }
        });
    }

    @Test
    void collect_noAnonymousIdCookie_issuesNewCookieAndReturns202() throws Exception {
        given(anonymousIdCookieFactory.resolve(any())).willReturn(null);
        given(anonymousIdCookieFactory.issue()).willReturn(ISSUED_ANONYMOUS_ID);
        given(anonymousIdCookieFactory.createCookie(ISSUED_ANONYMOUS_ID))
                .willReturn(org.springframework.http.ResponseCookie.from("anonymousId", ISSUED_ANONYMOUS_ID).build());

        mockMvc.perform(post("/funnel-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isAccepted())
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains(ISSUED_ANONYMOUS_ID));

        verify(funnelActionCollectorService).collect(any(), eq(ISSUED_ANONYMOUS_ID), isNull());
    }

    @Test
    void collect_existingAnonymousIdCookie_doesNotReissueCookie() throws Exception {
        given(anonymousIdCookieFactory.resolve(any())).willReturn("existing-anon-id");

        mockMvc.perform(post("/funnel-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isAccepted())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull());

        verify(funnelActionCollectorService).collect(any(), eq("existing-anon-id"), isNull());
    }

    @Test
    void collect_authenticatedMember_passesUserId() throws Exception {
        given(anonymousIdCookieFactory.resolve(any())).willReturn("existing-anon-id");
        principal = new AuthenticatedMemberDto(7L, PlatformRole.USER);

        mockMvc.perform(post("/funnel-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isAccepted());

        verify(funnelActionCollectorService).collect(any(), eq("existing-anon-id"), eq(7L));
    }

    @Test
    void collect_malformedSessionId_returns400() throws Exception {
        given(anonymousIdCookieFactory.resolve(any())).willReturn("existing-anon-id");
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {
            {
                put("sessionId", "not-a-uuid");
                put("eventId", 42L);
                put("actionType", "VIEW_EVENT_DETAIL");
                put("occurredAt", OffsetDateTime.now().toString());
                put("properties", java.util.Map.of());
            }
        });

        mockMvc.perform(post("/funnel-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
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
                return principal;
            }
        };
    }
}
