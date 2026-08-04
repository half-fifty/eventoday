package com.min.edu.booth.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothStatus;
import com.min.edu.booth.dto.BoothBulkCreateRequestDto;
import com.min.edu.booth.dto.BoothCreateRequestDto;
import com.min.edu.booth.dto.BoothIntroUpdateRequestDto;
import com.min.edu.booth.dto.BoothPageResponse;
import com.min.edu.booth.dto.BoothQrResponseDto;
import com.min.edu.booth.dto.BoothResponseDto;
import com.min.edu.booth.dto.BoothStatusUpdateRequestDto;
import com.min.edu.booth.dto.BoothUpdateRequestDto;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.support.BoothQrTokenGenerator;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.repository.OrganizationMemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BoothRepository boothRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final BoothQrTokenGenerator boothQrTokenGenerator;
    private final ObjectMapper objectMapper;

    @Transactional
    public BoothResponseDto create(Long eventId, BoothCreateRequestDto request, AuthenticatedMemberDto member) {
        requireEventExists(eventId);
        requireEventManager(eventId, member);

        if (boothRepository.existsByEventIdAndBoothCode(eventId, request.getBoothCode())) {
            throw new BusinessException(GlobalErrorCode.BOOTH_CODE_ALREADY_EXISTS);
        }

        Booth booth = Booth.create(
            eventId,
            request.getBoothCode(),
            request.getBoothType(),
            request.getFloorName(),
            request.getZoneName(),
            request.getLocationDescription(),
            request.getWidthMeter(),
            request.getDepthMeter(),
            request.getAreaSqm(),
            toJson(request.getBasicEquipment()),
            request.isElectricityAvailable(),
            request.isWaterAvailable(),
            request.isDrainageAvailable(),
            request.isInternetAvailable(),
            request.getPrice(),
            OffsetDateTime.now()
        );

        try {
            boothRepository.saveAndFlush(booth);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(GlobalErrorCode.BOOTH_CODE_ALREADY_EXISTS);
        }

        return toResponse(booth);
    }

    @Transactional
    public List<BoothResponseDto> createBulk(
            Long eventId,
            BoothBulkCreateRequestDto request,
            AuthenticatedMemberDto member) {
        requireEventExists(eventId);
        requireEventManager(eventId, member);

        List<String> boothCodes = request.getBoothCodes();
        Set<String> uniqueCodes = new HashSet<>(boothCodes);
        if (uniqueCodes.size() != boothCodes.size()) {
            throw new BusinessException(GlobalErrorCode.BOOTH_CODE_ALREADY_EXISTS);
        }

        for (String code : boothCodes) {
            if (boothRepository.existsByEventIdAndBoothCode(eventId, code)) {
                throw new BusinessException(GlobalErrorCode.BOOTH_CODE_ALREADY_EXISTS);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        String equipmentJson = toJson(request.getBasicEquipment());

        List<Booth> booths = new ArrayList<>();
        for (String code : boothCodes) {
            booths.add(Booth.create(
                eventId,
                code,
                request.getBoothType(),
                request.getFloorName(),
                request.getZoneName(),
                request.getLocationDescription(),
                request.getWidthMeter(),
                request.getDepthMeter(),
                request.getAreaSqm(),
                equipmentJson,
                request.isElectricityAvailable(),
                request.isWaterAvailable(),
                request.isDrainageAvailable(),
                request.isInternetAvailable(),
                request.getPrice(),
                now
            ));
        }

        try {
            boothRepository.saveAllAndFlush(booths);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(GlobalErrorCode.BOOTH_CODE_ALREADY_EXISTS);
        }

        return booths.stream().map(this::toResponse).toList();
    }

    @Transactional
    public BoothResponseDto update(
            Long eventId,
            Long boothId,
            BoothUpdateRequestDto request,
            AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        Booth booth = getByIdAndEventIdOrThrow(boothId, eventId);

        if (!booth.getBoothCode().equals(request.getBoothCode())
                && boothRepository.existsByEventIdAndBoothCode(eventId, request.getBoothCode())) {
            throw new BusinessException(GlobalErrorCode.BOOTH_CODE_ALREADY_EXISTS);
        }

        booth.updateDetails(
            request.getBoothCode(),
            request.getBoothType(),
            request.getFloorName(),
            request.getZoneName(),
            request.getLocationDescription(),
            request.getWidthMeter(),
            request.getDepthMeter(),
            request.getAreaSqm(),
            toJson(request.getBasicEquipment()),
            request.isElectricityAvailable(),
            request.isWaterAvailable(),
            request.isDrainageAvailable(),
            request.isInternetAvailable(),
            request.getPrice(),
            OffsetDateTime.now()
        );

        try {
            boothRepository.saveAndFlush(booth);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(GlobalErrorCode.BOOTH_CODE_ALREADY_EXISTS);
        }

        return toResponse(booth);
    }

    @Transactional
    public BoothResponseDto updateStatus(
            Long eventId,
            Long boothId,
            BoothStatusUpdateRequestDto request,
            AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        Booth booth = getByIdAndEventIdOrThrow(boothId, eventId);

        booth.changeStatus(request.getStatus(), OffsetDateTime.now());

        return toResponse(booth);
    }

    @Transactional
    public void delete(Long eventId, Long boothId, AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        Booth booth = getByIdAndEventIdOrThrow(boothId, eventId);

        if (booth.getStatus() != BoothStatus.AVAILABLE) {
            throw new BusinessException(GlobalErrorCode.BOOTH_DELETE_NOT_ALLOWED);
        }

        boothRepository.delete(booth);
    }

    @Transactional(readOnly = true)
    public BoothResponseDto getDetail(Long eventId, Long boothId, AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        Booth booth = getByIdAndEventIdOrThrow(boothId, eventId);

        return toResponse(booth);
    }

    @Transactional
    public BoothResponseDto updateIntro(
            Long eventId,
            Long boothId,
            BoothIntroUpdateRequestDto request,
            AuthenticatedMemberDto member) {
        Booth booth = getByIdAndEventIdOrThrow(boothId, eventId);
        requireAssignedOrganizationMemberOrEventManager(eventId, booth, member);

        if (booth.getStatus() != BoothStatus.ASSIGNED || booth.getAssignedOrganizationId() == null) {
            throw new BusinessException(GlobalErrorCode.BOOTH_NOT_ASSIGNED);
        }

        booth.updateIntro(
            request.getDisplayName(),
            request.getShortIntro(),
            request.getDescription(),
            request.getExhibitionContent(),
            request.getRepresentativeFileId(),
            OffsetDateTime.now()
        );

        return toResponse(booth);
    }

    @Transactional
    public BoothQrResponseDto issueQr(Long eventId, Long boothId, AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        Booth booth = getByIdAndEventIdOrThrow(boothId, eventId);

        // 이미 발급된 QR이 있으면 새로 만들지 않고 그대로 반환한다(분실 시에는 재조회로 충분).
        if (booth.getQrToken() == null) {
            String token = boothQrTokenGenerator.generate();
            booth.issueQrToken(token, OffsetDateTime.now());
        }

        return new BoothQrResponseDto(booth.getId(), booth.getQrToken(), booth.getQrIssuedAt());
    }

    @Transactional(readOnly = true)
    public BoothQrResponseDto getQr(Long eventId, Long boothId, AuthenticatedMemberDto member) {
        Booth booth = getByIdAndEventIdOrThrow(boothId, eventId);
        requireAssignedOrganizationMemberOrEventManager(eventId, booth, member);

        return new BoothQrResponseDto(booth.getId(), booth.getQrToken(), booth.getQrIssuedAt());
    }

    @Transactional(readOnly = true)
    public BoothPageResponse list(
            Long eventId,
            BoothStatus status,
            String floorName,
            String zoneName,
            String keyword,
            int page,
            int size,
            AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        validatePageRequest(page, size);

        String normalizedKeyword = normalizeKeyword(keyword);
        Page<Booth> booths = boothRepository.search(
            eventId, status, floorName, zoneName, normalizedKeyword, PageRequest.of(page, size));

        return new BoothPageResponse(
            booths.getContent().stream().map(this::toResponse).toList(),
            booths.getNumber(),
            booths.getSize(),
            booths.getTotalElements(),
            booths.getTotalPages(),
            booths.isFirst(),
            booths.isLast(),
            booths.isEmpty()
        );
    }

    private Booth getByIdAndEventIdOrThrow(Long boothId, Long eventId) {
        return boothRepository.findByIdAndEventId(boothId, eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
    }

    private void requireEventExists(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }
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

    private void requireAssignedOrganizationMemberOrEventManager(
            Long eventId, Booth booth, AuthenticatedMemberDto member) {
        if (member.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return;
        }

        boolean isEventManager = eventMemberRepository
            .existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                eventId,
                member.getMemberId(),
                EventRole.EVENT_MANAGER
            );

        if (isEventManager) {
            return;
        }

        Long assignedOrganizationId = booth.getAssignedOrganizationId();
        boolean isAssignedOrganizationMember = assignedOrganizationId != null
            && organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatus(
                assignedOrganizationId,
                member.getMemberId(),
                OrganizationMemberStatus.ACTIVE
            );

        if (!isAssignedOrganizationMember) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return "%" + keyword.trim().toLowerCase() + "%";
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private BoothResponseDto toResponse(Booth booth) {
        return BoothResponseDto.builder()
            .id(booth.getId())
            .eventId(booth.getEventId())
            .assignedOrganizationId(booth.getAssignedOrganizationId())
            .boothCode(booth.getBoothCode())
            .boothType(booth.getBoothType())
            .floorName(booth.getFloorName())
            .zoneName(booth.getZoneName())
            .locationDescription(booth.getLocationDescription())
            .widthMeter(booth.getWidthMeter())
            .depthMeter(booth.getDepthMeter())
            .areaSqm(booth.getAreaSqm())
            .basicEquipment(fromJson(booth.getBasicEquipment()))
            .electricityAvailable(booth.isElectricityAvailable())
            .waterAvailable(booth.isWaterAvailable())
            .drainageAvailable(booth.isDrainageAvailable())
            .internetAvailable(booth.isInternetAvailable())
            .price(booth.getPrice())
            .status(booth.getStatus())
            .displayName(booth.getDisplayName())
            .shortIntro(booth.getShortIntro())
            .description(booth.getDescription())
            .exhibitionContent(booth.getExhibitionContent())
            .representativeFileId(booth.getRepresentativeFileId())
            .qrToken(booth.getQrToken())
            .qrIssuedAt(booth.getQrIssuedAt())
            .createdAt(booth.getCreatedAt())
            .updatedAt(booth.getUpdatedAt())
            .build();
    }

    private String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException e) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(
                json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (JacksonException e) {
            return Collections.emptyList();
        }
    }
}
