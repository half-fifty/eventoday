package com.min.edu.interest.service;

import com.min.edu.interest.dto.InterestBoothResponse;
import com.min.edu.interest.repository.BoothInterestRepository;
import com.min.edu.booth.domain.BoothInterest;
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
        if (boothInterestRepository.existsByMemberIdAndBoothId(memberId, boothId)) {
            return;
        }
        boothInterestRepository.save(BoothInterest.builder()
                .memberId(memberId)
                .boothId(boothId)
                .vacancyNotificationEnabled(false)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    public void remove(Long memberId, Long boothId) {
        boothInterestRepository.findByMemberIdAndBoothId(memberId, boothId)
                .ifPresent(boothInterestRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<InterestBoothResponse> getMyInterests(Long memberId) {
        return boothInterestRepository.findAllByMemberId(memberId)
                .stream()
                .map(interest -> InterestBoothResponse.builder()
                        .boothId(interest.getBoothId())
                        .displayName("부스")
                        .shortIntro("정보")
                        .vacancyNotificationEnabled(interest.isVacancyNotificationEnabled())
                        .build())
                .collect(Collectors.toList());
    }
}
