package com.min.edu.interest.service;

import com.min.edu.interest.dto.InterestBoothResponse;
import com.min.edu.interest.repository.BoothInterestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothInterestService {

    private final BoothInterestRepository boothInterestRepository;

    public void register(Long memberId, Long boothId) {
        boothInterestRepository.upsertInterest(memberId, boothId);
    }

    public void remove(Long memberId, Long boothId) {
        boothInterestRepository.findByMemberIdAndBoothId(memberId, boothId)
                .ifPresent(boothInterestRepository::delete);
    }

    @Transactional(readOnly = true)
public List<InterestBoothResponse> getMyInterests(Long memberId) {
    return boothInterestRepository.findInterestBoothsByMemberId(memberId);
}
}
