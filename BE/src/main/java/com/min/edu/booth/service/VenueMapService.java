package com.min.edu.booth.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothMapPosition;
import com.min.edu.booth.domain.VenueMap;
import com.min.edu.booth.domain.VenueMapStatus;
import com.min.edu.booth.domain.VenueMapType;
import com.min.edu.booth.dto.BoothMapPositionResponseDto;
import com.min.edu.booth.dto.BoothMapPositionUpsertRequestDto;
import com.min.edu.booth.dto.VenueMapCreateRequestDto;
import com.min.edu.booth.dto.VenueMapResponseDto;
import com.min.edu.booth.repository.BoothMapPositionRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.VenueMapRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.file.service.FileService;
import com.min.edu.member.domain.PlatformRole;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VenueMapService {

    private final VenueMapRepository venueMapRepository;
    private final BoothMapPositionRepository boothMapPositionRepository;
    private final BoothRepository boothRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public List<VenueMapResponseDto> list(Long eventId, AuthenticatedMemberDto member) {
        requireEventExists(eventId);
        requireEventManager(eventId, member);

        return venueMapRepository.findByEventIdOrderByFloorNameAscVersionDesc(eventId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public VenueMapResponseDto getPublished(Long eventId, VenueMapType mapType) {
        VenueMap venueMap = venueMapRepository
            .findFirstByEventIdAndMapTypeAndStatusOrderByVersionDesc(eventId, mapType, VenueMapStatus.PUBLISHED)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        return toResponse(venueMap);
    }

    @Transactional
    public VenueMapResponseDto create(Long eventId, VenueMapCreateRequestDto request, AuthenticatedMemberDto member) {
        requireEventExists(eventId);
        requireEventManager(eventId, member);
        fileService.assertAccessible(request.getImageFileId(), member.getMemberId());

        int nextVersion = venueMapRepository
            .findFirstByEventIdAndMapTypeAndFloorNameOrderByVersionDesc(eventId, request.getMapType(), request.getFloorName())
            .map(existing -> existing.getVersion() + 1)
            .orElse(1);

        OffsetDateTime now = OffsetDateTime.now();
        VenueMap venueMap = VenueMap.builder()
            .eventId(eventId)
            .mapType(request.getMapType())
            .floorName(request.getFloorName())
            .imageFileId(request.getImageFileId())
            .originalWidth(request.getOriginalWidth())
            .originalHeight(request.getOriginalHeight())
            .version(nextVersion)
            .status(VenueMapStatus.DRAFT)
            .createdAt(now)
            .updatedAt(now)
            .build();

        VenueMap saved = venueMapRepository.save(venueMap);
        return toResponse(saved);
    }

    @Transactional
    public VenueMapResponseDto publish(Long eventId, Long mapId, AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        VenueMap venueMap = getByIdAndEventIdOrThrow(mapId, eventId);

        OffsetDateTime now = OffsetDateTime.now();
        venueMapRepository
            .findFirstByEventIdAndMapTypeAndFloorNameAndStatus(
                eventId, venueMap.getMapType(), venueMap.getFloorName(), VenueMapStatus.PUBLISHED)
            .ifPresent(previous -> previous.unpublish(now));

        venueMap.publish(now);
        return toResponse(venueMap);
    }

    @Transactional
    public void delete(Long eventId, Long mapId, AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        VenueMap venueMap = getByIdAndEventIdOrThrow(mapId, eventId);

        if (venueMap.getStatus() != VenueMapStatus.DRAFT) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        boothMapPositionRepository.deleteByVenueMapId(venueMap.getId());
        venueMapRepository.delete(venueMap);
    }

    @Transactional
    public VenueMapResponseDto upsertPositions(
            Long eventId, Long mapId, BoothMapPositionUpsertRequestDto request, AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        VenueMap venueMap = getByIdAndEventIdOrThrow(mapId, eventId);

        OffsetDateTime now = OffsetDateTime.now();
        List<BoothMapPosition> positions = request.getPositions().stream()
            .map(item -> {
                boothRepository.findByIdAndEventId(item.getBoothId(), eventId)
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
                return BoothMapPosition.builder()
                    .venueMapId(venueMap.getId())
                    .boothId(item.getBoothId())
                    .xRatio(item.getXRatio())
                    .yRatio(item.getYRatio())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            })
            .toList();

        boothMapPositionRepository.deleteByVenueMapId(venueMap.getId());
        boothMapPositionRepository.saveAll(positions);

        return toResponse(venueMap);
    }

    private VenueMapResponseDto toResponse(VenueMap venueMap) {
        List<BoothMapPositionResponseDto> positions = boothMapPositionRepository.findByVenueMapId(venueMap.getId()).stream()
            .map(position -> {
                Booth booth = boothRepository.findByIdAndEventId(position.getBoothId(), venueMap.getEventId()).orElse(null);
                return BoothMapPositionResponseDto.builder()
                    .boothId(position.getBoothId())
                    .boothCode(booth != null ? booth.getBoothCode() : null)
                    .displayName(booth != null ? booth.getDisplayName() : null)
                    .xRatio(position.getXRatio())
                    .yRatio(position.getYRatio())
                    .build();
            })
            .toList();

        return VenueMapResponseDto.builder()
            .id(venueMap.getId())
            .eventId(venueMap.getEventId())
            .mapType(venueMap.getMapType())
            .floorName(venueMap.getFloorName())
            .imageFileId(venueMap.getImageFileId())
            .originalWidth(venueMap.getOriginalWidth())
            .originalHeight(venueMap.getOriginalHeight())
            .version(venueMap.getVersion())
            .status(venueMap.getStatus())
            .publishedAt(venueMap.getPublishedAt())
            .positions(positions)
            .build();
    }

    private VenueMap getByIdAndEventIdOrThrow(Long mapId, Long eventId) {
        return venueMapRepository.findByIdAndEventId(mapId, eventId)
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
}
