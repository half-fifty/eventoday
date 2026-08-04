package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationSlotStatus;
import com.min.edu.booth.dto.BoothReservationSlotResponse;
import com.min.edu.booth.dto.CreateBoothReservationSlotRequest;
import com.min.edu.booth.repository.BoothReservationSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothReservationSlotService {

    private final BoothReservationSlotRepository boothReservationSlotRepository;

    public BoothReservationSlotResponse createReservationSlot(
            Long boothId,
            CreateBoothReservationSlotRequest request) {

        // 유니크 제약 확인
        if (boothReservationSlotRepository.existsByBoothIdAndStartAt(boothId, request.getStartAt())) {
            throw new IllegalArgumentException("이미 존재하는 시간대입니다.");
        }

        // startAt < endAt 검증
        if (request.getStartAt().isAfter(request.getEndAt())) {
            throw new IllegalArgumentException("startAt은 endAt보다 작아야 합니다.");
        }

        BoothReservationSlot slot = BoothReservationSlot.builder()
                .boothId(boothId)
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .capacity(request.getCapacity())
                .reservedCount(0)
                .status(BoothReservationSlotStatus.OPEN)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        BoothReservationSlot saved = boothReservationSlotRepository.save(slot);

        return BoothReservationSlotResponse.builder()
                .id(saved.getId())
                .boothId(saved.getBoothId())
                .startAt(saved.getStartAt())
                .endAt(saved.getEndAt())
                .capacity(saved.getCapacity())
                .reservedCount(saved.getReservedCount())
                .status(saved.getStatus())
                .build();
    }
}