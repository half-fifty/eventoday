package com.min.edu.booth.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

        return toResponses(eventId, venueMapRepository.findByEventIdOrderByFloorNameAscVersionDesc(eventId));
    }

    // 같은 mapType이라도 층(floorName)별로 각각 게시될 수 있으므로, 게시된 모든 층을 반환한다.
    @Transactional(readOnly = true)
    public List<VenueMapResponseDto> getPublished(Long eventId, VenueMapType mapType) {
        List<VenueMap> venueMaps = venueMapRepository
            .findByEventIdAndMapTypeAndStatusOrderByFloorNameAsc(eventId, mapType, VenueMapStatus.PUBLISHED);

        return toResponses(eventId, venueMaps);
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
        return toResponses(eventId, List.of(saved)).get(0);
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
        return toResponses(eventId, List.of(venueMap)).get(0);
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

        List<BoothMapPositionUpsertRequestDto.PositionItem> items = request.getPositions();
        Set<Long> boothIds = items.stream()
            .map(BoothMapPositionUpsertRequestDto.PositionItem::getBoothId)
            .collect(Collectors.toSet());
        if (boothIds.size() != items.size()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 부스 하나씩 조회하지 않고 이벤트 소속 부스를 한 번에 조회해 요청에 포함된 모든 boothId가
        // 실제로 존재하는지 검증한다 (positions가 많을 때의 N+1 방지).
        Set<Long> existingBoothIds = boothRepository.findByEventIdAndIdIn(eventId, boothIds).stream()
            .map(Booth::getId)
            .collect(Collectors.toSet());
        if (!existingBoothIds.containsAll(boothIds)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<BoothMapPosition> positions = items.stream()
            .map(item -> BoothMapPosition.builder()
                .venueMapId(venueMap.getId())
                .boothId(item.getBoothId())
                .xRatio(item.getXRatio())
                .yRatio(item.getYRatio())
                .createdAt(now)
                .updatedAt(now)
                .build())
            .toList();

        // deleteByVenueMapId와 saveAll이 같은 트랜잭션에 있으면 Hibernate가 INSERT를 DELETE보다
        // 먼저 실행해 (venue_map_id, booth_id) unique 제약을 위반할 수 있어, 명시적으로 flush해
        // 삭제를 먼저 DB에 반영한다.
        boothMapPositionRepository.deleteByVenueMapId(venueMap.getId());
        boothMapPositionRepository.flush();
        boothMapPositionRepository.saveAll(positions);

        return toResponses(eventId, List.of(venueMap)).get(0);
    }

    // venueMap마다 좌표/부스를 개별 조회하면 N+1이 발생하므로, 대상 평면도들의 좌표와
    // 관련 부스를 각각 한 번씩만 조회해 메모리에서 조립한다.
    private List<VenueMapResponseDto> toResponses(Long eventId, List<VenueMap> venueMaps) {
        if (venueMaps.isEmpty()) {
            return List.of();
        }

        List<Long> mapIds = venueMaps.stream().map(VenueMap::getId).toList();
        List<BoothMapPosition> allPositions = boothMapPositionRepository.findByVenueMapIdIn(mapIds);

        Set<Long> boothIds = allPositions.stream().map(BoothMapPosition::getBoothId).collect(Collectors.toSet());
        Map<Long, Booth> boothsById = boothIds.isEmpty()
            ? Map.of()
            : boothRepository.findByEventIdAndIdIn(eventId, boothIds).stream()
                .collect(Collectors.toMap(Booth::getId, booth -> booth));

        Map<Long, List<BoothMapPosition>> positionsByMapId = allPositions.stream()
            .collect(Collectors.groupingBy(BoothMapPosition::getVenueMapId));

        return venueMaps.stream()
            .map(venueMap -> {
                List<BoothMapPositionResponseDto> positions = positionsByMapId
                    .getOrDefault(venueMap.getId(), List.of()).stream()
                    .map(position -> {
                        Booth booth = boothsById.get(position.getBoothId());
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
            })
            .toList();
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
