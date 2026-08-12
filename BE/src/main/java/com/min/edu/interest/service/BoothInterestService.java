package com.min.edu.interest.service;

import com.min.edu.booth.domain.BoothInterest;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.interest.dto.InterestBoothResponse;
import com.min.edu.interest.repository.BoothInterestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothInterestService {

    private final BoothInterestRepository boothInterestRepository;

    public void register(Long memberId, Long boothId) {
        BoothInterest interest = boothInterestRepository.findByMemberIdAndBoothId(memberId, boothId)
                .orElse(BoothInterest.builder()
                        .memberId(memberId)
                        .boothId(boothId)
                        .vacancyNotificationEnabled(false)
                        .createdAt(OffsetDateTime.now())
                        .build());

        boothInterestRepository.saveAndFlush(interest);
    }

    public void remove(Long memberId, Long boothId) {
        boothInterestRepository.findByMemberIdAndBoothId(memberId, boothId)
                .ifPresent(boothInterestRepository::delete);
    }

    @Transactional(readOnly = true)
public List<InterestBoothResponse> getMyInterests(Long memberId) {
    return boothInterestRepository.findInterestBoothsByMemberId(memberId);
}

    // 관심 등록한 부스에 대해 빈자리 알림 수신 여부를 켜고 끈다. 관심 등록 자체가 안 돼있으면 실패.
    public void updateVacancyNotification(Long memberId, Long boothId, boolean enabled) {
        BoothInterest interest = boothInterestRepository.findByMemberIdAndBoothId(memberId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        interest.updateVacancyNotificationEnabled(enabled);
        boothInterestRepository.saveAndFlush(interest);
    }





}
