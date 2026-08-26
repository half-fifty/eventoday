package com.min.edu.recruitment.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.recruitment.dto.BoothRecruitmentCreateRequestDto;
import com.min.edu.recruitment.dto.BoothRecruitmentEndAtUpdateRequestDto;
import com.min.edu.recruitment.dto.BoothRecruitmentResponseDto;
import com.min.edu.recruitment.dto.BoothRecruitmentUpdateRequestDto;
import com.min.edu.recruitment.repository.BoothRecruitmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothRecruitmentService {

    private final BoothRecruitmentRepository boothRecruitmentRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final BoothRepository boothRepository;

    @Transactional
    public BoothRecruitmentResponseDto create(
            Long eventId,
            BoothRecruitmentCreateRequestDto request,
            AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        validatePeriod(request.getRecruitmentStartAt(), request.getRecruitmentEndAt());
        validateBeforeEventEnd(request.getRecruitmentEndAt(), event);

        if (boothRecruitmentRepository.existsByEventId(eventId)) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_ALREADY_EXISTS);
        }

        // 부스가 하나도 없는 행사에 모집 공고부터 올라가면, 지원자가 지원할 부스 자체가
        // 없는 상태로 공고가 노출된다 - 부스 관리에서 최소 1개는 미리 등록해두게 한다.
        if (!boothRepository.existsByEventId(eventId)) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_REQUIRES_AT_LEAST_ONE_BOOTH);
        }

        OffsetDateTime now = OffsetDateTime.now();
        BoothRecruitment recruitment = BoothRecruitment.create(
            eventId,
            request.getTitle(),
            request.getRecruitmentStartAt(),
            request.getRecruitmentEndAt(),
            request.getParticipantTarget(),
            request.getQualification(),
            request.getSelectionMethod(),
            request.getExpectedDecisionAt(),
            request.getContactName(),
            request.getContactEmail(),
            request.getContactPhone(),
            request.getNotice(),
            request.getBusinessNumberRequired(),
            now
        );

        try {
            boothRecruitmentRepository.saveAndFlush(recruitment);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_ALREADY_EXISTS);
        }

        return BoothRecruitmentResponseDto.from(recruitment);
    }

    @Transactional
    public BoothRecruitmentResponseDto update(
            Long eventId,
            BoothRecruitmentUpdateRequestDto request,
            AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        BoothRecruitment recruitment = getByEventIdOrThrow(eventId);
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (recruitment.getStatus() != BoothRecruitmentStatus.BEFORE_OPEN) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_STATUS_TRANSITION_INVALID);
        }

        validatePeriod(request.getRecruitmentStartAt(), request.getRecruitmentEndAt());
        validateBeforeEventEnd(request.getRecruitmentEndAt(), event);

        recruitment.updateDetails(
            request.getTitle(),
            request.getRecruitmentStartAt(),
            request.getRecruitmentEndAt(),
            request.getParticipantTarget(),
            request.getQualification(),
            request.getSelectionMethod(),
            request.getExpectedDecisionAt(),
            request.getContactName(),
            request.getContactEmail(),
            request.getContactPhone(),
            request.getNotice(),
            request.getBusinessNumberRequired(),
            OffsetDateTime.now()
        );

        return BoothRecruitmentResponseDto.from(recruitment);
    }

    @Transactional
    public BoothRecruitmentResponseDto updateEndAt(
            Long eventId,
            BoothRecruitmentEndAtUpdateRequestDto request,
            AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        BoothRecruitment recruitment = getByEventIdOrThrow(eventId);
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (recruitment.getStatus() != BoothRecruitmentStatus.OPEN) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_STATUS_TRANSITION_INVALID);
        }

        OffsetDateTime now = OffsetDateTime.now();
        validatePeriod(recruitment.getRecruitmentStartAt(), request.getRecruitmentEndAt());
        validateBeforeEventEnd(request.getRecruitmentEndAt(), event);
        if (!request.getRecruitmentEndAt().isAfter(now)) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_PERIOD_INVALID);
        }

        recruitment.updateEndAt(request.getRecruitmentEndAt(), now);

        return BoothRecruitmentResponseDto.from(recruitment);
    }

    @Transactional
    public BoothRecruitmentResponseDto close(Long eventId, AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        BoothRecruitment recruitment = getByEventIdOrThrow(eventId);

        if (recruitment.getStatus() != BoothRecruitmentStatus.BEFORE_OPEN
                && recruitment.getStatus() != BoothRecruitmentStatus.OPEN) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_STATUS_TRANSITION_INVALID);
        }

        recruitment.changeStatus(BoothRecruitmentStatus.CLOSED, OffsetDateTime.now());

        return BoothRecruitmentResponseDto.from(recruitment);
    }

    @Transactional
    public BoothRecruitmentResponseDto complete(Long eventId, AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        BoothRecruitment recruitment = getByEventIdOrThrow(eventId);

        if (recruitment.getStatus() != BoothRecruitmentStatus.CLOSED) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_STATUS_TRANSITION_INVALID);
        }

        recruitment.complete(OffsetDateTime.now());

        return BoothRecruitmentResponseDto.from(recruitment);
    }

    @Transactional
    public void delete(Long eventId, AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        BoothRecruitment recruitment = getByEventIdOrThrow(eventId);

        if (recruitment.getStatus() != BoothRecruitmentStatus.BEFORE_OPEN) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_STATUS_TRANSITION_INVALID);
        }

        boothRecruitmentRepository.delete(recruitment);
    }

    @Transactional(readOnly = true)
    public BoothRecruitmentResponseDto getManagement(Long eventId, AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        return BoothRecruitmentResponseDto.from(getByEventIdOrThrow(eventId));
    }

    @Transactional(readOnly = true)
    public BoothRecruitmentResponseDto getPublicDetail(Long recruitmentId) {
        BoothRecruitment recruitment = boothRecruitmentRepository.findById(recruitmentId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (recruitment.getStatus() == BoothRecruitmentStatus.BEFORE_OPEN) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        Event event = eventRepository.findById(recruitment.getEventId()).orElse(null);

        return BoothRecruitmentResponseDto.from(recruitment, event);
    }

    @Transactional(readOnly = true)
    public List<BoothRecruitmentResponseDto> listPublic(BoothRecruitmentStatus status) {
        List<BoothRecruitment> recruitments;

        if (status == null) {
            recruitments = boothRecruitmentRepository.findAllByStatusNot(BoothRecruitmentStatus.BEFORE_OPEN);
        } else if (status == BoothRecruitmentStatus.BEFORE_OPEN) {
            recruitments = List.of();
        } else {
            recruitments = boothRecruitmentRepository.findAllByStatus(status);
        }

        return recruitments.stream()
            .map(BoothRecruitmentResponseDto::from)
            .toList();
    }

    private BoothRecruitment getByEventIdOrThrow(Long eventId) {
        return boothRecruitmentRepository.findByEventId(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
    }

    private void requireEventManager(Long eventId, AuthenticatedMemberDto member) {
        if (member.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return;
        }

        boolean isEventManager = eventMemberRepository
            .existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                eventId,
                member.getMemberId(),
                EventRole.EVENT_MANAGER
            );

        if (!isEventManager) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private void validatePeriod(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_PERIOD_INVALID);
        }
    }

    private void validateBeforeEventEnd(OffsetDateTime recruitmentEndAt, Event event) {
        if (recruitmentEndAt.isAfter(event.getEndAt())) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_PERIOD_INVALID);
        }
    }
}
