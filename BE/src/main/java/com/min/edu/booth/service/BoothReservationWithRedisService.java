package com.min.edu.booth.service;

import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.domain.BoothReservationSlotStatus;
import com.min.edu.booth.dto.BoothReservationAdminResponse;
import com.min.edu.booth.dto.BoothReservationListResponse;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.dto.CreateBoothReservationRequest;
import com.min.edu.booth.event.BoothVacancyEvent;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReservationRepository;
import com.min.edu.booth.repository.BoothReservationSlotRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.Member;
import com.min.edu.member.repository.MemberRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothReservationWithRedisService {

    private final BoothReservationRepository reservationRepository;
    private final BoothReservationSlotRepository slotRepository;
    private final BoothRepository boothRepository;
    private final BoothManagerPermissionChecker boothManagerPermissionChecker;
    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final AdmissionTicketRepository admissionTicketRepository;
    private final RedisReservationService redisReservationService;
    private final ApplicationEventPublisher eventPublisher;

    // 상태 무관 조회: 한 번이라도 예약한 적이 있으면(취소 포함) 그 부스는 재예약이 불가능하므로,
    // 프론트에서 "이미 예약했던 부스" 상태를 구분해서 보여줄 수 있도록 상태와 무관하게 반환한다.
    @Transactional(readOnly = true)
    public BoothReservationResponse getMyReservation(Long boothId, Long memberId) {
        return reservationRepository.findByMemberIdAndBoothId(memberId, boothId)
                .map(this::toResponse)
                .orElse(null);
    }

    // 운영자: 부스 예약자 전체 목록 (누가, 언제, 몇 명) 조회
    @Transactional(readOnly = true)
    public List<BoothReservationAdminResponse> listReservationsForManager(
            Long boothId, AuthenticatedMemberDto principal) {

        boothManagerPermissionChecker.requireBoothManager(boothId, principal);

        List<BoothReservation> reservations = reservationRepository.findAllByBoothIdOrderByReservedAtDesc(boothId);
        if (reservations.isEmpty()) {
            return List.of();
        }

        Map<Long, BoothReservationSlot> slotsById = slotRepository
                .findAllByBoothIdOrderByStartAtAsc(boothId).stream()
                .collect(Collectors.toMap(BoothReservationSlot::getId, Function.identity()));

        List<Long> memberIds = reservations.stream().map(BoothReservation::getMemberId).distinct().toList();
        Map<Long, Member> membersById = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        return reservations.stream()
                .map(reservation -> toAdminResponse(
                        reservation,
                        slotsById.get(reservation.getBoothReservationSlotId()),
                        membersById.get(reservation.getMemberId())))
                .toList();
    }

    // 회원의 모든 부스 예약 목록 (행사 전체에 걸쳐, 최신순) - "내 예약 목록" 화면용
    @Transactional(readOnly = true)
    public Page<BoothReservationListResponse> listMyReservations(Long memberId, Pageable pageable) {
        Page<BoothReservation> reservations = reservationRepository.findAllByMemberIdOrderByReservedAtDescIdDesc(memberId, pageable);

        List<Long> boothIds = reservations.getContent().stream()
                .map(BoothReservation::getBoothId).distinct().toList();
        Map<Long, Booth> boothsById = boothRepository.findAllById(boothIds).stream()
                .collect(Collectors.toMap(Booth::getId, Function.identity()));

        List<Long> slotIds = reservations.getContent().stream()
                .map(BoothReservation::getBoothReservationSlotId).distinct().toList();
        Map<Long, BoothReservationSlot> slotsById = slotRepository.findAllById(slotIds).stream()
                .collect(Collectors.toMap(BoothReservationSlot::getId, Function.identity()));

        List<Long> eventIds = boothsById.values().stream()
                .map(Booth::getEventId).distinct().toList();
        Map<Long, Event> eventsById = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));

        return reservations.map(reservation -> {
            Booth booth = boothsById.get(reservation.getBoothId());
            BoothReservationSlot slot = slotsById.get(reservation.getBoothReservationSlotId());
            Event event = booth != null ? eventsById.get(booth.getEventId()) : null;
            return toListResponse(reservation, booth, slot, event);
        });
    }

    private BoothReservationListResponse toListResponse(
            BoothReservation reservation, Booth booth, BoothReservationSlot slot, Event event) {
        return BoothReservationListResponse.builder()
                .id(reservation.getId())
                .boothId(reservation.getBoothId())
                .boothCode(booth != null ? booth.getBoothCode() : null)
                .boothDisplayName(booth != null ? booth.getDisplayName() : null)
                .eventId(event != null ? event.getId() : null)
                .eventName(event != null ? event.getName() : null)
                .slotId(reservation.getBoothReservationSlotId())
                .slotStartAt(slot != null ? slot.getStartAt() : null)
                .slotEndAt(slot != null ? slot.getEndAt() : null)
                .partySize(reservation.getPartySize())
                .status(reservation.getStatus())
                .reservedAt(reservation.getReservedAt())
                .cancelledAt(reservation.getCancelledAt())
                .checkedInAt(reservation.getCheckedInAt())
                .noShowAt(reservation.getNoShowAt())
                .build();
    }

    // 운영자: 예약자 출석 수동 체크 (true=방문 확인, false=노쇼 처리)
    @Transactional
    public BoothReservationAdminResponse markAttendance(
            Long boothId, Long reservationId, boolean attended, AuthenticatedMemberDto principal) {

        boothManagerPermissionChecker.requireBoothManager(boothId, principal);

        BoothReservation reservation = reservationRepository.findByIdWithLock(reservationId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        if (!reservation.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        if (reservation.getStatus() != BoothReservationStatus.RESERVED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        OffsetDateTime now = OffsetDateTime.now();

        if (attended) {
            reservation.updateStatus(BoothReservationStatus.CHECKED_IN);
            reservation.updateCheckedInAt(now);
        } else {
            reservation.updateStatus(BoothReservationStatus.NO_SHOW);
            reservation.updateNoShowAt(now);

            // 노쇼 처리 시 슬롯 자리 복원 (비관적 잠금)
            BoothReservationSlot slot = slotRepository.findByIdWithLock(reservation.getBoothReservationSlotId())
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
            if (slot.getReservedCount() < reservation.getPartySize()) {
                throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
            }
            slot.decrementReservedCount(reservation.getPartySize());
            slotRepository.saveAndFlush(slot);
        }
        reservation.updateUpdatedAt(now);
        reservationRepository.saveAndFlush(reservation);

        BoothReservationSlot slot = slotRepository.findById(reservation.getBoothReservationSlotId()).orElse(null);
        Member member = memberRepository.findById(reservation.getMemberId()).orElse(null);

        return toAdminResponse(reservation, slot, member);
    }

    // 운영자 화면에는 예약자 식별에 필요한 최소 정보만 노출한다 (이메일은 마스킹).
    private BoothReservationAdminResponse toAdminResponse(
            BoothReservation reservation, BoothReservationSlot slot, Member member) {
        return BoothReservationAdminResponse.builder()
                .id(reservation.getId())
                .slotId(reservation.getBoothReservationSlotId())
                .startAt(slot != null ? slot.getStartAt() : null)
                .endAt(slot != null ? slot.getEndAt() : null)
                .memberId(reservation.getMemberId())
                .memberNickname(member != null ? member.getNickname() : null)
                .memberEmail(member != null ? maskEmail(member.getEmail()) : null)
                .partySize(reservation.getPartySize())
                .status(reservation.getStatus())
                .reservedAt(reservation.getReservedAt())
                .cancelledAt(reservation.getCancelledAt())
                .build();
    }

    // ab****@example.com 형태로 마스킹 - 로컬 파트 앞 2글자만 남긴다.
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        String localPart = email.substring(0, atIndex);
        String visible = localPart.substring(0, Math.min(2, localPart.length()));
        return visible + "*".repeat(Math.max(localPart.length() - visible.length(), 1)) + email.substring(atIndex);
    }

    /**
     * WBS-146: Redis 선점을 포함한 예약 생성 (통합 서비스)
     *
     * Redis에 임시 선점 시도
     * 선점 성공 → DB에 예약 저장
     * ⭐ DB 트랜잭션 커밋 후 afterCompletion에서 Redis 선점 해제
     *
     * 예외 발생 시: try-catch에서 지금 바로 해제 (트랜잭션 롤백 전)
     *
     * Controller에서는 이 메서드만 호출하면 됨 (Redis 처리 X)
     */
    @Transactional
    public BoothReservationResponse createReservationWithRedis(
            Long boothId,
            CreateBoothReservationRequest request,
            Long memberId) {

        OffsetDateTime now = OffsetDateTime.now();

        // 부스 존재 확인
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 행사가 취소되었거나 이미 종료됐으면 예약 불가 (Redis 선점을 시도하기 전에 먼저 걸러낸다)
        Event event = eventRepository.findById(booth.getEventId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        boolean eventAvailable = event.getStatus() != EventStatus.CANCELLED
                && event.getStatus() != EventStatus.ENDED
                && event.getEndAt().isAfter(now);
        if (!eventAvailable) {
            throw new BusinessException(GlobalErrorCode.BOOTH_RESERVATION_EVENT_NOT_AVAILABLE);
        }

        // 티켓 구매자만 예약 가능 (해당 행사의 AdmissionTicket 보유 여부로 확인)
        if (!admissionTicketRepository.existsByMemberIdAndEventId(memberId, event.getId())) {
            throw new BusinessException(GlobalErrorCode.BOOTH_RESERVATION_TICKET_REQUIRED);
        }

        // 슬롯 확인 (비관적 잠금으로 동시성 제어)
        BoothReservationSlot slot = slotRepository.findByIdWithLock(request.getSlotId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 슬롯의 부스가 맞는지 확인 (booth ownership)
        if (!slot.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 슬롯 상태 확인 (OPEN이어야 함)
        if (slot.getStatus() != BoothReservationSlotStatus.OPEN) {
            throw new BusinessException(GlobalErrorCode.BOOTH_RESERVATION_SLOT_NOT_OPEN);
        }

        // partySize가 남은 자리를 초과하는지 확인
        int remainingCapacity = slot.getCapacity() - slot.getReservedCount();
        if (request.getPartySize() > remainingCapacity) {
            throw new BusinessException(GlobalErrorCode.BOOTH_RESERVATION_SLOT_FULL);
        }

        // 한 번이라도 예약한 적이 있는 부스는(취소된 예약 포함) 재예약을 허용하지 않는다.
        if (reservationRepository.existsByMemberIdAndBoothId(memberId, boothId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // Redis 선점 (WBS-146) — 같은 회원이 더블클릭 등으로 같은 슬롯에 요청을 중복으로 밀어넣는 것을
        // 막는 용도. 키가 회원별로 분리돼 있어 다른 회원의 정당한 예약 시도를 막지는 않는다.
        boolean reserved = redisReservationService.reserveSlot(boothId, request.getSlotId(), memberId);
        if (!reserved) {
            throw new BusinessException(GlobalErrorCode.RESERVATION_ALREADY_EXISTS);  // 방금 보낸 요청이 아직 처리 중
        }

        try {
            // DB에 예약 저장
            BoothReservation reservation = BoothReservation.builder()
                    .boothId(boothId)
                    .boothReservationSlotId(request.getSlotId())
                    .memberId(memberId)
                    .partySize(request.getPartySize())
                    .status(BoothReservationStatus.RESERVED)
                    .createdAt(now)
                    .reservedAt(now)
                    .updatedAt(now)
                    .build();

            BoothReservation saved;
            try {
                saved = reservationRepository.saveAndFlush(reservation);
            } catch (DataIntegrityViolationException e) {
                // 동시 요청이 사전 중복 검사를 함께 통과한 경우, uk_booth_reservations 제약 위반을 409로 변환
                throw new BusinessException(GlobalErrorCode.RESERVATION_ALREADY_EXISTS, e);
            }

            // 슬롯의 남은 자리 감소 (이미 비관적 잠금으로 보호됨)
            slot.incrementReservedCount(request.getPartySize());
            slotRepository.saveAndFlush(slot);

            // ⭐ DB 트랜잭션 커밋 후 Redis 선점 해제 등록
            // 성공·실패 모두 afterCompletion에서 한 번만 수행
            registerRedisReleaseOnCommit(boothId, request.getSlotId(), memberId);

            return toResponse(saved);

        } catch (Exception e) {
            // ⚠️ 예약 생성 실패 시 (트랜잭션 커밋 전 실패)
            // 지금 바로 Redis 선점 해제 (트랜잭션 롤백 전)
            redisReservationService.releaseSlot(boothId, request.getSlotId(), memberId);
            throw e;
        }
    }

    /**
     * 예약 취소 (이벤트 발행 포함)
     * - 예약 상태 변경 (RESERVED → CANCELLED)
     * - 슬롯 자리 복원
     * - 빈자리 알림 이벤트 발행
     * - ⭐ 트랜잭션 커밋 후 afterCompletion에서 Redis 선점 해제
     */
    @Transactional
    public void cancelReservation(Long reservationId, Long boothId, Long memberId) {
        // 1️⃣ 예약 조회 (비관적 잠금)
        BoothReservation reservation = reservationRepository.findByIdAndMemberIdWithLock(reservationId, memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2boothId 검증
        if (!reservation.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 예약 상태 확인
        if (reservation.getStatus() != BoothReservationStatus.RESERVED) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 예약 상태 변경 (CANCELLED)
        reservation.updateStatus(BoothReservationStatus.CANCELLED);
        reservation.updateCancelledAt(now);
        reservation.updateUpdatedAt(now);
        reservationRepository.saveAndFlush(reservation);

        // 5️⃣ 슬롯 자리 복원 (비관적 잠금)
        BoothReservationSlot slot = slotRepository.findByIdWithLock(reservation.getBoothReservationSlotId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // ⭐ lower-bound 검증: 예약 인원 수보다 현재 예약 수가 작으면 에러
        if (slot.getReservedCount() < reservation.getPartySize()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        slot.decrementReservedCount(reservation.getPartySize());
        slotRepository.saveAndFlush(slot);

        // 6️⃣ ⭐ 트랜잭션 커밋 후 Redis 선점 해제 등록
        // 성공·실패 모두 afterCompletion에서 한 번만 수행
        registerRedisReleaseOnCommit(boothId, reservation.getBoothReservationSlotId(), memberId);

        // 7️⃣ ⭐ 빈자리 알림 이벤트 발행
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        eventPublisher.publishEvent(new BoothVacancyEvent(
                this,
                boothId,
                reservation.getBoothReservationSlotId(),
                reservationId,
                booth.getDisplayName()
        ));
    }

    /**
     * ⭐ TransactionSynchronizationManager를 사용한 Redis 해제 등록
     *
     * DB 트랜잭션 커밋 이후 Redis 선점 해제
     * 성공·실패 모두 afterCompletion에서 한 번만 수행
     *
     * - 커밋 성공: afterCompletion 실행 → Redis 해제
     * - 롤백 실패: afterCompletion 실행 → Redis 해제
     * 결과: 중복 해제 절대 불가능!
     */
    private void registerRedisReleaseOnCommit(Long boothId, Long slotId, Long memberId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            redisReservationService.releaseSlot(boothId, slotId, memberId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        // status: STATUS_COMMITTED (성공) 또는 STATUS_ROLLED_BACK (실패)
                        // 둘 다 여기서 한 번만 실행됨
                        redisReservationService.releaseSlot(boothId, slotId, memberId);
                    }
                }
        );
    }

    private BoothReservationResponse toResponse(BoothReservation reservation) {
        return BoothReservationResponse.builder()
                .id(reservation.getId())
                .boothId(reservation.getBoothId())
                .slotId(reservation.getBoothReservationSlotId())
                .memberId(reservation.getMemberId())
                .partySize(reservation.getPartySize())
                .status(reservation.getStatus())
                .reservedAt(reservation.getReservedAt())
                .cancelledAt(reservation.getCancelledAt())
                .checkedInAt(reservation.getCheckedInAt())
                .noShowAt(reservation.getNoShowAt())
                .build();
    }
}