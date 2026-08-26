package com.min.edu.booth.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.ai.GeminiVisionClient;
import com.min.edu.booth.ai.dto.DetectedBoothBox;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothMapPosition;
import com.min.edu.booth.domain.VenueMap;
import com.min.edu.booth.domain.VenueMapStatus;
import com.min.edu.booth.domain.VenueMapType;
import com.min.edu.booth.dto.BoothMapPositionResponseDto;
import com.min.edu.booth.dto.BoothMapPositionUpsertRequestDto;
import com.min.edu.booth.dto.VenueMapAutoLayoutSuggestionDto;
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
import com.min.edu.file.dto.FileMetaResponseDto;
import com.min.edu.file.service.FileService;
import com.min.edu.member.domain.PlatformRole;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VenueMapService {

    private final VenueMapRepository venueMapRepository;
    private final BoothMapPositionRepository boothMapPositionRepository;
    private final BoothRepository boothRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final FileService fileService;
    private final GeminiVisionClient geminiVisionClient;

    // 저장소 자체 presigned URL을 내려받는 용도라 요청마다 설정을 주입받을 필요는 없지만,
    // 다른 외부 호출들과 마찬가지로 타임아웃 없이 무한정 대기하지 않도록 명시적으로 둔다.
    private final RestClient imageDownloadClient = buildImageDownloadClient();

    private static RestClient buildImageDownloadClient() {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

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

        VenueMap saved;
        try {
            saved = venueMapRepository.saveAndFlush(venueMap);
        } catch (DataIntegrityViolationException e) {
            // FK 위반 등 다른 무결성 예외까지 버전 충돌로 오인하지 않도록, uk_venue_maps_event_type_floor_version
            // 유니크 제약 위반인 경우에만 VENUE_MAP_VERSION_CONFLICT로 변환하고 나머지는 그대로 전파한다.
            if (isVenueMapVersionUniqueViolation(e)) {
                throw new BusinessException(GlobalErrorCode.VENUE_MAP_VERSION_CONFLICT, e);
            }
            throw e;
        }
        return toResponses(eventId, List.of(saved)).get(0);
    }

    // Vision으로 좌표를 "제안"만 하고 저장하지는 않는다 - 라벨 오독(I/1 혼동 등) 가능성이
    // 실측으로 확인됐기 때문에, 최종 저장은 관리자가 upsertPositions로 직접 확정해야 한다.
    //
    // 이 메서드 전체를 @Transactional로 묶지 않는다 - Gemini 호출은 45초까지 걸릴 수 있는데,
    // 트랜잭션으로 감싸면 그 시간 내내 커넥션 풀에서 DB 커넥션을 하나 붙잡고 있게 되어(기본
    // 풀 크기는 10) 동시 요청 몇 건만으로도 앱 전체의 DB 커넥션이 고갈될 수 있다. 아래
    // 리포지토리 호출들은 각자 Spring Data JPA가 제공하는 자체 짧은 트랜잭션으로 충분하다.
    public List<VenueMapAutoLayoutSuggestionDto> suggestAutoLayout(
            Long eventId, Long mapId, AuthenticatedMemberDto member) {
        requireEventManager(eventId, member);
        VenueMap venueMap = getByIdAndEventIdOrThrow(mapId, eventId);

        FileMetaResponseDto fileMeta = fileService.getFileMeta(venueMap.getImageFileId(), member.getMemberId());
        byte[] imageBytes = downloadImage(fileMeta.getDownloadUrl());

        List<DetectedBoothBox> detections = geminiVisionClient.detectBooths(imageBytes, fileMeta.getMimeType());

        Map<String, Booth> boothsByNormalizedCode = buildBoothsByNormalizedCode(eventId);

        return detections.stream()
            .map(detection -> {
                Booth matched = boothsByNormalizedCode.get(normalizeBoothCode(detection.label()));
                return VenueMapAutoLayoutSuggestionDto.builder()
                    .label(detection.label())
                    .boothId(matched != null ? matched.getId() : null)
                    .boothCode(matched != null ? matched.getBoothCode() : null)
                    .matched(matched != null)
                    .xRatio(detection.xRatio())
                    .yRatio(detection.yRatio())
                    .build();
            })
            .toList();
    }

    // 정규화했을 때 코드가 겹치는 부스(예: "A-01"과 "A01")가 있으면 어느 쪽에 매칭시켜야
    // 할지 알 수 없다. 하나를 임의로 골라 조용히 틀린 부스에 매칭시키는 대신, 그런 라벨은
    // 아예 매칭 후보에서 빼서 관리자가 직접 배치하게 한다.
    private Map<String, Booth> buildBoothsByNormalizedCode(Long eventId) {
        Map<String, List<Booth>> grouped = boothRepository.findByEventId(eventId).stream()
            .filter(booth -> booth.getBoothCode() != null)
            .collect(Collectors.groupingBy(booth -> normalizeBoothCode(booth.getBoothCode())));

        grouped.forEach((normalized, booths) -> {
            if (booths.size() > 1) {
                log.warn(
                    "부스 코드가 정규화 후 충돌하여 자동 배치 매칭에서 제외합니다. eventId={}, normalized={}, boothCodes={}",
                    eventId, normalized, booths.stream().map(Booth::getBoothCode).toList());
            }
        });

        return grouped.entrySet().stream()
            .filter(entry -> entry.getValue().size() == 1)
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get(0)));
    }

    // 부스 코드와 Vision이 읽은 라벨을 대소문자·구분자 차이 없이 매칭하기 위한 정규화.
    private String normalizeBoothCode(String code) {
        return code == null ? "" : code.trim().toUpperCase().replaceAll("[\\s-]", "");
    }

    private byte[] downloadImage(String url) {
        try {
            // Presigned URL은 AWS 서명 쿼리 파라미터(X-Amz-Credential 등)가 이미 퍼센트 인코딩되어
            // 있다. .uri(String)으로 넘기면 RestClient가 이를 템플릿으로 보고 다시 인코딩해
            // 서명이 깨지므로, 인코딩을 건드리지 않는 URI 객체로 그대로 넘긴다.
            return imageDownloadClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
        } catch (RestClientException e) {
            log.warn("평면도 이미지 다운로드에 실패했습니다. message={}", e.getMessage(), e);
            throw new BusinessException(GlobalErrorCode.VENUE_MAP_AUTO_LAYOUT_UNAVAILABLE, e);
        }
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

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String VENUE_MAP_VERSION_UNIQUE_CONSTRAINT =
        "uk_venue_maps_event_type_floor_version";

    private boolean isVenueMapVersionUniqueViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                return VENUE_MAP_VERSION_UNIQUE_CONSTRAINT.equals(
                    constraintViolationException.getConstraintName()
                ) && UNIQUE_VIOLATION_SQL_STATE.equals(
                    constraintViolationException.getSQLState()
                );
            }

            current = current.getCause();
        }

        return false;
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
