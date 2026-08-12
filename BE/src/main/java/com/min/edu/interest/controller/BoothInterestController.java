package com.min.edu.interest.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.interest.dto.InterestBoothResponse;
import com.min.edu.interest.dto.UpdateVacancyNotificationRequest;
import com.min.edu.interest.service.BoothInterestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/booths")
public class BoothInterestController {

    private final BoothInterestService boothInterestService;

    @PostMapping("/{boothId}/interests")
    public ResponseEntity<Void> register(
            @PathVariable Long boothId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {
        requireAuthenticated(principal);
        boothInterestService.register(principal.getMemberId(), boothId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{boothId}/interests")
    public ResponseEntity<Void> remove(
            @PathVariable Long boothId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {
        requireAuthenticated(principal);
        boothInterestService.remove(principal.getMemberId(), boothId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/interests")
    public ResponseEntity<List<InterestBoothResponse>> getMyInterests(
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {
        requireAuthenticated(principal);
        return ResponseEntity.ok(boothInterestService.getMyInterests(principal.getMemberId()));
    }

    // 관심 등록한 부스의 빈자리 알림 수신 여부 켜기/끄기.
    @PatchMapping("/{boothId}/interests/vacancy-notification")
    public ResponseEntity<Void> updateVacancyNotification(
            @PathVariable Long boothId,
            @RequestBody @Valid UpdateVacancyNotificationRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {
        requireAuthenticated(principal);
        boothInterestService.updateVacancyNotification(principal.getMemberId(), boothId, request.getEnabled());
        return ResponseEntity.noContent().build();
    }

    // 이 컨트롤러의 모든 엔드포인트가 SecurityConfig의 명시적 인증 목록에 없어(기본값 permitAll),
    // principal이 null일 수 있다 - 컨트롤러에서 직접 401로 거부해야 NPE로 500이 나는 걸 막을 수 있다.
    private void requireAuthenticated(AuthenticatedMemberDto principal) {
        if (principal == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }
}