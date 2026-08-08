package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothInterest;
import com.min.edu.interest.repository.BoothInterestRepository;
import com.min.edu.notification.domain.Notification;
import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothVacancyNotificationService {

    private final BoothInterestRepository boothInterestRepository;
    private final NotificationRepository notificationRepository;

    public void notifyVacancy(Long boothId, String boothName) {
        // 1. 해당 부스에 관심 + 알림 활성화한 회원들 조회
        List<BoothInterest> interests =
                boothInterestRepository.findByBoothIdAndVacancyNotificationEnabledTrue(boothId);

        // 2. 각 회원에게 알림 생성
        for (BoothInterest interest : interests) {
            String title = boothName + " 부스에 빈자리가 생겼습니다.";
            String content = "예약이 취소되어 빈자리가 생겼습니다. 지금 예약하세요!";

            notificationRepository.insertIfAbsent(
                    UUID.randomUUID(),                              // eventId (중복 방지)
                    interest.getMemberId(),                         // memberId
                    NotificationType.BOOTH_VACANCY_AVAILABLE.name(), // notificationType
                    "BOOTH",                                        // referenceType
                    boothId,                                        // referenceId
                    title,                                          // title
                    content,                                        // content
                    OffsetDateTime.now()                           // createdAt
            );
        }
    }
}
