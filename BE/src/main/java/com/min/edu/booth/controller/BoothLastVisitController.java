package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.LastVisitedBoothResponse;
import com.min.edu.booth.service.BoothLastVisitService;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/booths")
public class BoothLastVisitController {

    private final BoothLastVisitService boothLastVisitService;

    // 이 컨트롤러의 엔드포인트가 SecurityConfig의 명시적 인증 목록에 없어(기본값 permitAll),
    // principal이 null일 수 있다 - BoothInterestController와 같은 이유로 여기서 직접 401로
    // 거부해야 principal.getMemberId()에서 NPE로 500이 나는 걸 막을 수 있다.
    @GetMapping("/last-visited")
    public ResponseEntity<LastVisitedBoothResponse> getLastVisitedBooth(
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        if (principal == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        // 방문 이력이 없으면 null을 그대로 200으로 내려준다 - "아직 없음"은 정상적인 빈 상태이지,
        // 에러가 아니다.
        LastVisitedBoothResponse response = boothLastVisitService.getLastVisitedBooth(principal.getMemberId());
        return ResponseEntity.ok(response);
    }
}