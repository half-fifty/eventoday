package com.min.edu.booth.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationSlotStatus;
import com.min.edu.booth.dto.BoothReservationSlotResponse;
import com.min.edu.booth.dto.CreateBoothReservationSlotRequest;
import com.min.edu.booth.dto.UpdateBoothReservationSlotRequest;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReservationRepository;
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
    private final BoothReservationRepository boothReservationRepository;
    private final BoothRepository boothRepository;
    private final BoothOrganizationMemberRepository boothOrganizationMemberRepository;

    @Transactional(readOnly = true)
    public List<BoothReservationSlotResponse> listSlots(Long boothId) {
        return boothReservationSlotRepository.findAllByBoothIdOrderByStartAtAsc(boothId).stream()
                .map(this::toResponse)
                .toList();
    }

    public BoothReservationSlotResponse createReservationSlot(
            Long boothId,
            CreateBoothReservationSlotRequest request,
            AuthenticatedMemberDto actor) {

        requireBoothManager(boothId, actor);

        if (boothReservationSlotRepository.existsByBoothIdAndStartAt(boothId, request.getStartAt())) {
            throw new BusinessException(GlobalErrorCode.RESERVATION_SLOT_TIME_CONFLICT);
        }

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

    @Transactional
    public BoothReservationSlotResponse updateReservationSlot(
            Long boothId,
            Long slotId,
            UpdateBoothReservationSlotRequest request,
            AuthenticatedMemberDto principal) {

        requireBoothManager(boothId, principal);

        BoothReservationSlot slot = boothReservationSlotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (!slot.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        if (slot.getStatus() == BoothReservationSlotStatus.CLOSED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        if (request.getCapacity() < slot.getReservedCount()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        if (!slot.getStartAt().equals(request.getStartAt())) {
            if (boothReservationSlotRepository.existsByBoothIdAndStartAtAndIdNot(
                    slot.getBoothId(), request.getStartAt(), slotId)) {
                throw new BusinessException(GlobalErrorCode.RESERVATION_SLOT_TIME_CONFLICT);
            }
        }

        if (!request.getStartAt().isBefore(request.getEndAt())) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
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

        BoothReservationSlot saved = boothReservationSlotRepository.saveAndFlush(updated);
        return toResponse(saved);
    }

    @Transactional
    public BoothReservationSlotResponse closeReservationSlot(
            Long boothId,
            Long slotId,
            AuthenticatedMemberDto principal) {

        requireBoothManager(boothId, principal);

        BoothReservationSlot slot = boothReservationSlotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (!slot.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

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

        BoothReservationSlot saved = boothReservationSlotRepository.saveAndFlush(closed);
        return toResponse(saved);
    }

    @Transactional
    public BoothReservationSlotResponse reopenReservationSlot(
            Long boothId,
            Long slotId,
            AuthenticatedMemberDto principal) {

        requireBoothManager(boothId, principal);

        BoothReservationSlot slot = boothReservationSlotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (!slot.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        if (slot.getStatus() == BoothReservationSlotStatus.OPEN) {
            return toResponse(slot);
        }

        BoothReservationSlot reopened = BoothReservationSlot.builder()
                .id(slot.getId())
                .boothId(slot.getBoothId())
                .startAt(slot.getStartAt())
                .endAt(slot.getEndAt())
                .capacity(slot.getCapacity())
                .reservedCount(slot.getReservedCount())
                .status(BoothReservationSlotStatus.OPEN)
                .createdAt(slot.getCreatedAt())
                .updatedAt(OffsetDateTime.now())
                .build();

        BoothReservationSlot saved = boothReservationSlotRepository.saveAndFlush(reopened);
        return toResponse(saved);
    }

    @Transactional
    public void deleteReservationSlot(Long boothId, Long slotId, AuthenticatedMemberDto principal) {
        requireBoothManager(boothId, principal);

        BoothReservationSlot slot = boothReservationSlotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (!slot.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        // 취소된 예약도 FK로 이 슬롯을 참조하고 있어 삭제할 수 없다. 대신 on/off로 끄도록 안내한다.
        if (boothReservationRepository.existsByBoothReservationSlotId(slotId)) {
            throw new BusinessException(GlobalErrorCode.RESERVATION_SLOT_HAS_RESERVATIONS);
        }

        boothReservationSlotRepository.delete(slot);
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