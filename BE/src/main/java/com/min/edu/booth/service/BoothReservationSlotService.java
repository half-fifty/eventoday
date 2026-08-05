package com.min.edu.booth.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationSlotStatus;
import com.min.edu.booth.dto.BoothReservationSlotResponse;
import com.min.edu.booth.dto.CreateBoothReservationSlotRequest;
import com.min.edu.booth.dto.UpdateBoothReservationSlotRequest;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReservationSlotRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import com.min.edu.booth.repository.BoothOrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothReservationSlotService {

    private static final List<OrganizationRole> MANAGER_ROLES =
            List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);

    private final BoothReservationSlotRepository boothReservationSlotRepository;
    private final BoothRepository boothRepository;
    private final BoothOrganizationMemberRepository boothOrganizationMemberRepository;

    public BoothReservationSlotResponse createReservationSlot(
            Long boothId,
            CreateBoothReservationSlotRequest request,
            AuthenticatedMemberDto actor) {

        requireBoothManager(boothId, actor);

        // 유니크 제약 확인 (동시 요청은 아래 save의 DB 제약 위반 처리로 방어)
        if (boothReservationSlotRepository.existsByBoothIdAndStartAt(boothId, request.getStartAt())) {
            throw new BusinessException(GlobalErrorCode.RESERVATION_SLOT_TIME_CONFLICT);
        }

        // startAt < endAt 검증 (같은 경우도 거부)
        if (!request.getStartAt().isBefore(request.getEndAt())) {
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

        BoothReservationSlot saved;
        try {
            saved = boothReservationSlotRepository.saveAndFlush(slot);
        } catch (DataIntegrityViolationException e) {
            if (isSlotUniqueConstraintViolation(e)) {
                throw new BusinessException(GlobalErrorCode.RESERVATION_SLOT_TIME_CONFLICT, e);
            }
            throw e;
        }

        return toResponse(saved);
    }

    public BoothReservationSlotResponse updateReservationSlot(
            Long boothId,
            Long slotId,
            UpdateBoothReservationSlotRequest request,
            AuthenticatedMemberDto actor) {

        requireBoothManager(boothId, actor);

        BoothReservationSlot slot = getSlot(boothId, slotId);

        // 마감된 시간대는 수정 불가
        if (slot.getStatus() == BoothReservationSlotStatus.CLOSED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // startAt < endAt 검증 (같은 경우도 거부)
        if (!request.getStartAt().isBefore(request.getEndAt())) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 이미 예약된 인원보다 정원을 줄일 수 없음
        if (request.getCapacity() < slot.getReservedCount()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 자기 자신을 제외한 시간대 중복 확인 (동시 요청은 아래 flush의 DB 제약 위반 처리로 방어)
        if (boothReservationSlotRepository.existsByBoothIdAndStartAtAndIdNot(
                boothId, request.getStartAt(), slotId)) {
            throw new BusinessException(GlobalErrorCode.RESERVATION_SLOT_TIME_CONFLICT);
        }

        BoothReservationSlot updated = BoothReservationSlot.builder()
                .id(slot.getId())
                .boothId(slot.getBoothId())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .capacity(request.getCapacity())
                .reservedCount(slot.getReservedCount())
                .status(slot.getStatus())
                .createdAt(slot.getCreatedAt())
                .updatedAt(OffsetDateTime.now())
                .build();

        BoothReservationSlot saved;
        try {
            saved = boothReservationSlotRepository.saveAndFlush(updated);
        } catch (DataIntegrityViolationException e) {
            if (isSlotUniqueConstraintViolation(e)) {
                throw new BusinessException(GlobalErrorCode.RESERVATION_SLOT_TIME_CONFLICT, e);
            }
            throw e;
        }

        return toResponse(saved);
    }

    public BoothReservationSlotResponse closeReservationSlot(
            Long boothId,
            Long slotId,
            AuthenticatedMemberDto actor) {

        requireBoothManager(boothId, actor);

        BoothReservationSlot slot = getSlot(boothId, slotId);

        // 이미 마감된 경우 그대로 반환 (멱등 처리)
        if (slot.getStatus() == BoothReservationSlotStatus.CLOSED) {
            return toResponse(slot);
        }

        BoothReservationSlot closed = BoothReservationSlot.builder()
                .id(slot.getId())
                .boothId(slot.getBoothId())
                .startAt(slot.getStartAt())
                .endAt(slot.getEndAt())
                .capacity(slot.getCapacity())
                .reservedCount(slot.getReservedCount())
                .status(BoothReservationSlotStatus.CLOSED)
                .createdAt(slot.getCreatedAt())
                .updatedAt(OffsetDateTime.now())
                .build();

        return toResponse(boothReservationSlotRepository.save(closed));
    }

    private BoothReservationSlot getSlot(Long boothId, Long slotId) {
        BoothReservationSlot slot = boothReservationSlotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        // 다른 부스의 슬롯 ID로 접근하는 경우 차단
        if (!slot.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }
        return slot;
    }

    private BoothReservationSlotResponse toResponse(BoothReservationSlot slot) {
        return BoothReservationSlotResponse.builder()
                .id(slot.getId())
                .boothId(slot.getBoothId())
                .startAt(slot.getStartAt())
                .endAt(slot.getEndAt())
                .capacity(slot.getCapacity())
                .reservedCount(slot.getReservedCount())
                .status(slot.getStatus())
                .build();
    }

    private boolean isSlotUniqueConstraintViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConstraintViolationException) {
                String constraintName = ((ConstraintViolationException) cause).getConstraintName();
                if (constraintName != null
                        && constraintName.toLowerCase().contains("uk_booth_reservation_slots")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void requireBoothManager(Long boothId, AuthenticatedMemberDto actor) {
        if (actor == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        Long organizationId = booth.getAssignedOrganizationId();
        if (organizationId == null
                || !boothOrganizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                organizationId, actor.getMemberId(), OrganizationMemberStatus.ACTIVE, MANAGER_ROLES)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }
}