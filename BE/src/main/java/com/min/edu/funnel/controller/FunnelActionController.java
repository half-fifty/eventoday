package com.min.edu.funnel.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.funnel.dto.FunnelActionRequest;
import com.min.edu.funnel.service.FunnelActionCollectorService;
import com.min.edu.funnel.support.AnonymousIdCookieFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FunnelActionController {

    private final FunnelActionCollectorService funnelActionCollectorService;
    private final AnonymousIdCookieFactory anonymousIdCookieFactory;

    @PostMapping("/funnel-actions")
    public ResponseEntity<Void> collect(
            @Valid @RequestBody FunnelActionRequest request,
            HttpServletRequest httpServletRequest,
            @AuthenticationPrincipal AuthenticatedMemberDto member) {

        String anonymousId = anonymousIdCookieFactory.resolve(httpServletRequest);
        boolean isNewAnonymousId = anonymousId == null;
        if (isNewAnonymousId) {
            anonymousId = anonymousIdCookieFactory.issue();
        }

        Long userId = member == null ? null : member.getMemberId();
        funnelActionCollectorService.collect(request, anonymousId, userId);

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.accepted();
        if (isNewAnonymousId) {
            ResponseCookie cookie = anonymousIdCookieFactory.createCookie(anonymousId);
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return responseBuilder.build();
    }
}
