package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothInterest;
import com.min.edu.interest.repository.BoothInterestRepository;
import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothVacancyNotificationService {

    private final BoothInterestRepository boothInterestRepository;
    private final NotificationRepository notificationRepository;

    /**
     * WBS-?: idempotencyKey를 사용한 알림 생성 (멱등성 보장)
     *
     * idempotencyKey를 notifyVacancy로 전달하여
     * 각 회원별 고유한 eventId를 생성
     * → 같은 이벤트 재시도 시 중복 알림 방지
     */
    public void notifyVacancyWithIdempotency(Long boothId, String displayName, Long slotId, Long idempotencyKey) {
        // ⭐ idempotencyKey를 전달하여 멱등성 보장
        notifyVacancy(boothId, displayName, idempotencyKey);
    }

    /**
     * 부스 빈자리 알림 (멱등성 보장)
     *
     * 1. 해당 부스에 관심 있는 회원들 조회
     * 2. 각 회원별로 고유한 eventId 생성
     *    - idempotencyKey + memberId 기반 (결정적)
     *    - 재시도 시에도 같은 ID 생성 → insertIfAbsent로 중복 방지
     * 3. 회원별 알림 생성 (멱등성)
     */
    public void notifyVacancy(Long boothId, String boothName, Long idempotencyKey) {
        // 1️⃣ 해당 부스에 관심 + 알림 활성화한 회원들 조회
        List<BoothInterest> interests =
                boothInterestRepository.findByBoothIdAndVacancyNotificationEnabledTrue(boothId);

        // 2️⃣ 각 회원에게 알림 생성 (회원별 고유 key)
        for (BoothInterest interest : interests) {
            Long memberId = interest.getMemberId();

            // ⭐ idempotencyKey + memberId로 결정적 eventId 생성
            // 같은 이벤트(idempotencyKey)에 같은 회원(memberId)이면 항상 같은 eventId
            Long deterministicEventId = generateDeterministicEventId(idempotencyKey, memberId);

            String title = boothName + " 부스에 빈자리가 생겼습니다.";
            String content = "예약이 취소되어 빈자리가 생겼습니다. 지금 예약하세요!";

            // ⭐ insertIfAbsentWithLongEventId: 같은 eventId면 중복 저장 안 함 (DB unique constraint)
            notificationRepository.insertIfAbsentWithLongEventId(
                    deterministicEventId,                               // eventId (idempotencyKey + memberId 기반, Long)
                    memberId,                                           // memberId
                    NotificationType.BOOTH_VACANCY_AVAILABLE.name(),   // notificationType
                    "BOOTH",                                            // referenceType
                    boothId,                                            // referenceId
                    title,                                              // title
                    content,                                            // content
                    OffsetDateTime.now()                               // createdAt
            );
        }
    }

    /**
     * ⭐ 결정적 eventId 생성 (멱등성 보장)
     *
     * idempotencyKey와 memberId를 조합하여 고정된 ID 생성
     * - 같은 입력 → 같은 출력 (deterministic)
     * - SHA-256 기반 해싱으로 충돌 위험 최소화
     * - 각 회원별로 고유한 ID (per-member notification 유지)
     *
     * 예시:
     * - idempotencyKey=100, memberId=1 → 항상 같은 ID 생성
     * - idempotencyKey=100, memberId=2 → 다른 ID 생성
     * - 재시도 시에도 같은 ID → insertIfAbsent로 중복 방지
     */
    private Long generateDeterministicEventId(Long idempotencyKey, Long memberId) {
        try {
            // idempotencyKey:memberId 조합
            String input = idempotencyKey + ":" + memberId;

            // SHA-256 해싱
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // 처음 8바이트를 Long으로 변환 (양수 보장)
            long eventId = 0;
            for (int i = 0; i < 8; i++) {
                eventId = (eventId << 8) | (hashBytes[i] & 0xFF);
            }

            // 양수 보장 (첫 번째 비트 0)
            return Math.abs(eventId);

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 사용 불가 시 폴백 (거의 발생 안 함)
            // 간단한 해싱으로 대체
            String input = idempotencyKey + ":" + memberId;
            return Math.abs((long) input.hashCode());
        }
    }
}