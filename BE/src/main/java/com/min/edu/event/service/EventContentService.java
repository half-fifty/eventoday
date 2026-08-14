package com.min.edu.event.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventContent;
import com.min.edu.event.domain.EventContentAudience;
import com.min.edu.event.domain.EventContentType;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.dto.EventContentDtos;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.event.repository.EventAdmissionTicketRepository;
import com.min.edu.event.repository.EventBoothAssignmentRepository;
import com.min.edu.event.repository.EventContentRepository;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.file.domain.FileAccessLevel;
import com.min.edu.file.domain.FileAsset;
import com.min.edu.file.repository.FileAssetRepository;
import com.min.edu.file.service.FileService;
import com.min.edu.file.storage.FileStorageService;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventContentService {

    // 전체 공지 목록(CONTENT-API-006) 페이지 크기 상한 - BoothApplicationService와 동일
    private static final int MAX_PAGE_SIZE = 100;

    // 관람객(VISITOR) 판정 시 유효한 입장권으로 인정하는 상태.
    // 취소·만료 티켓 보유자는 관람객으로 보지 않는다.
    private static final List<AdmissionTicketStatus> VALID_TICKET_STATUSES =
            List.of(AdmissionTicketStatus.ISSUED, AdmissionTicketStatus.USED);

    private final EventContentRepository eventContentRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventOrganizationMemberRepository eventOrganizationMemberRepository;
    private final EventBoothAssignmentRepository eventBoothAssignmentRepository;
    private final EventAdmissionTicketRepository eventAdmissionTicketRepository;
    private final FileService fileService;
    private final FileAssetRepository fileAssetRepository;
    private final FileStorageService fileStorageService;
    // 첨부파일 교체 시 기존 파일 정리를 커밋 이후로 미루기 위한 이벤트 발행
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 공지·자료 목록 조회
     *
     * 권한별 audience 필터링 (판정 규칙은 {@link #resolveAllowedAudiences} 참고):
     * - PLATFORM_ADMIN 또는 해당 행사 EVENT_MANAGER → ALL / EXHIBITOR / VISITOR 전체 조회
     * - 일반 회원 → ALL + 참가기업·관람객 해당 시 각 audience 추가 조회
     * - 비로그인 → ALL audience만 조회
     *
     * @param eventId     행사 ID
     * @param contentType 콘텐츠 유형 필터 (null이면 전체)
     * @param member      인증 회원 (비로그인이면 null)
     */
    public List<EventContentDtos.Summary> listContents(
            Long eventId,
            EventContentType contentType,
            AuthenticatedMemberDto member) {

        // 행사 존재 여부 확인
        if (!eventRepository.existsById(eventId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        // 접근 가능한 audience 목록 결정
        List<EventContentAudience> allowedAudiences = resolveAllowedAudiences(eventId, member);

        // Specification 조건 구성 (EventService의 Specification 패턴 참고)
        Specification<EventContent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("eventId"), eventId));
            predicates.add(root.get("audience").in(allowedAudiences));
            if (contentType != null) {
                // contentType 파라미터가 있으면 추가 필터링
                predicates.add(cb.equal(root.get("contentType"), contentType));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        // 상단 고정 공지 우선(pinned DESC), 최신순(publishedAt DESC) 정렬
        Sort sort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("publishedAt"));

        List<EventContent> contents = eventContentRepository.findAll(spec, sort);

        // 첨부파일 메타(원본명·크기)를 일괄 조회해 응답에 포함 (파일별 N+1 쿼리 방지)
        Map<Long, FileAsset> fileAssets = loadFileAssets(contents);

        return contents.stream()
                .map(c -> toSummary(c, fileAssets))
                .toList();
    }

    /**
     * 전체 공지·자료 목록 조회 (CONTENT-API-006)
     *
     * 공개(PUBLISHED) 행사의 공지·자료를 행사 이름과 함께 페이지 단위로 반환한다.
     * 공지사항 페이지(/notices)가 행사별로 N번 호출하던 것을 1회 호출로 대체.
     *
     * 공개 API이므로 페이지 크기를 MAX_PAGE_SIZE로 제한한다
     * (행사·콘텐츠 증가에 따라 응답 크기와 DB 부하가 무제한으로 커지는 것을 방지)
     *
     * audience 정책: PLATFORM_ADMIN은 전체, 그 외(비로그인 포함)는 ALL만 노출
     *
     * @param contentType 콘텐츠 유형 필터 (null이면 전체)
     * @param page        페이지 번호 (0부터)
     * @param size        페이지 크기 (최대 MAX_PAGE_SIZE)
     * @param member      인증 회원 (비로그인이면 null)
     */
    public EventContentDtos.BoardPageResponse listAllContents(
            EventContentType contentType, int page, int size, AuthenticatedMemberDto member) {

        // 페이지 파라미터 검증 (BoothApplicationService.validatePageRequest 패턴)
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 전체 목록은 공개 페이지 용도이므로 ALL audience만 (PLATFORM_ADMIN은 전체)
        List<EventContentAudience> allowedAudiences =
                (member != null && member.getPlatformRole() == PlatformRole.PLATFORM_ADMIN)
                        ? Arrays.asList(EventContentAudience.values())
                        : List.of(EventContentAudience.ALL);

        // 공개(PUBLISHED) 행사 소속 콘텐츠만 서브쿼리로 필터링
        // (행사 ID를 전부 메모리로 올리지 않도록 DB 단에서 처리)
        Specification<EventContent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            Subquery<Long> publishedEventIds = query.subquery(Long.class);
            Root<Event> eventRoot = publishedEventIds.from(Event.class);
            publishedEventIds.select(eventRoot.get("id"))
                    .where(cb.equal(eventRoot.get("status"), EventStatus.PUBLISHED));
            predicates.add(root.get("eventId").in(publishedEventIds));

            predicates.add(root.get("audience").in(allowedAudiences));
            if (contentType != null) {
                predicates.add(cb.equal(root.get("contentType"), contentType));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        // 상단 고정 우선(pinned DESC), 최신순(publishedAt DESC)
        Sort sort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("publishedAt"));
        Page<EventContent> result =
                eventContentRepository.findAll(spec, PageRequest.of(page, size, sort));

        List<EventContent> contents = result.getContent();

        // 현재 페이지에 등장한 행사만 이름 조회 (전체 행사 로드 방지)
        Map<Long, String> eventNames = loadEventNames(contents);

        // 첨부파일 메타 일괄 조회 (N+1 방지)
        Map<Long, FileAsset> fileAssets = loadFileAssets(contents);

        List<EventContentDtos.BoardItem> items = contents.stream()
                .map(c -> new EventContentDtos.BoardItem(
                        eventNames.get(c.getEventId()),
                        toSummary(c, fileAssets)))
                .toList();

        return new EventContentDtos.BoardPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                result.isEmpty()
        );
    }

    /** 현재 페이지 콘텐츠의 eventId만 모아 행사 이름을 일괄 조회 (eventId → 행사명) */
    private Map<Long, String> loadEventNames(List<EventContent> contents) {
        List<Long> eventIds = contents.stream()
                .map(EventContent::getEventId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Event::getName));
    }

    /**
     * 맵에서 파일 메타 조회 (null 안전)
     * Map.of()로 만든 불변 맵은 get(null) 호출 시 NPE를 던지므로
     * fileId가 없는(첨부 없는) 콘텐츠는 맵 조회 없이 null 반환
     */
    private FileAsset findFileAsset(Map<Long, FileAsset> fileAssets, Long fileId) {
        return fileId != null ? fileAssets.get(fileId) : null;
    }

    /**
     * 첨부파일 Presigned URL 생성 (CONTENT-006/007)
     *
     * 콘텐츠 첨부는 PRIVATE으로 저장되어 /v1/files 다운로드로는 업로더 본인만 접근할 수 있다.
     * audience 검증을 통과한 요청에만 URL을 발급해 실제 열람 권한과 일치시킨다.
     */
    private String presignedUrl(FileAsset fileAsset) {
        return fileAsset != null ? fileStorageService.generatePresignedUrl(fileAsset.getStorageKey()) : null;
    }

    /** FileAsset 조회와 URL 발급을 함께 처리해 목록 변환에서 재사용한다 */
    private EventContentDtos.Summary toSummary(EventContent content, Map<Long, FileAsset> fileAssets) {
        FileAsset fileAsset = findFileAsset(fileAssets, content.getFileId());
        return EventContentDtos.Summary.from(content, fileAsset, presignedUrl(fileAsset));
    }

    /** 콘텐츠 목록의 fileId를 모아 FileAsset을 일괄 조회 (fileId → FileAsset 맵) */
    private Map<Long, FileAsset> loadFileAssets(List<EventContent> contents) {
        List<Long> fileIds = contents.stream()
                .map(EventContent::getFileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return fileAssetRepository.findAllById(fileIds).stream()
                .collect(Collectors.toMap(FileAsset::getId, f -> f));
    }

    /**
     * 공지·자료 상세 조회
     *
     * 권한별 audience 접근 제어 (판정 규칙은 {@link #resolveAllowedAudiences} 참고):
     * - PLATFORM_ADMIN / 해당 행사 EVENT_MANAGER → audience 제한 없음
     * - 일반 회원 → ALL + 참가기업·관람객 해당 시 각 audience 접근 가능
     * - 비로그인 → ALL만 접근 가능
     * 허용되지 않은 audience의 콘텐츠에 접근하면 403
     *
     * @param contentId 콘텐츠 ID
     * @param member    인증 회원 (비로그인이면 null)
     */
    public EventContentDtos.Summary getContent(Long contentId, AuthenticatedMemberDto member) {

        // 콘텐츠 조회
        EventContent content = eventContentRepository.findById(contentId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 접근 가능한 audience 목록 결정 후 권한 검증
        List<EventContentAudience> allowedAudiences =
                resolveAllowedAudiences(content.getEventId(), member);

        if (!allowedAudiences.contains(content.getAudience())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        // 첨부파일이 있으면 원본명·크기 포함
        FileAsset fileAsset = content.getFileId() != null
                ? fileAssetRepository.findById(content.getFileId()).orElse(null)
                : null;

        return EventContentDtos.Summary.from(content, fileAsset, presignedUrl(fileAsset));
    }

    /**
     * 공지·자료 등록 (CONTENT-API-003)
     * EVENT_MANAGER 또는 PLATFORM_ADMIN만 등록 가능
     * 파일이 있으면 업로드 후 fileId를 콘텐츠에 연결
     * 트랜잭션 롤백 시 업로드된 S3 파일 보상 삭제
     *
     * @param eventId 행사 ID
     * @param request 등록 요청 DTO
     * @param file    첨부파일 (선택)
     * @param member  인증 회원
     */
    @Transactional
    public EventContentDtos.Summary createContent(
            Long eventId,
            EventContentDtos.CreateRequest request,
            MultipartFile file,
            AuthenticatedMemberDto member) {

        // 비로그인 요청 401 처리
        requireAuthenticated(member);

        // 행사 존재 여부 확인
        if (!eventRepository.existsById(eventId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        // EVENT_MANAGER 또는 PLATFORM_ADMIN만 등록 가능
        if (member.getPlatformRole() != PlatformRole.PLATFORM_ADMIN
                && !eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                eventId, member.getMemberId(), EventRole.EVENT_MANAGER)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        // 파일이 있으면 업로드 후 fileId 획득 (롤백 보상을 위해 FileAsset 보관)
        Long fileId = null;
        FileAsset uploadedAsset = null;
        String uploadedStorageKey = null;
        if (file != null && !file.isEmpty()) {
            fileId = fileService.upload(file, FileAccessLevel.PRIVATE, member.getMemberId()).getFileId();
            uploadedAsset = fileAssetRepository.findById(fileId).orElse(null);
            uploadedStorageKey = uploadedAsset != null ? uploadedAsset.getStorageKey() : null;
        }

        OffsetDateTime now = OffsetDateTime.now();
        EventContent content = EventContent.create(
                eventId,
                member.getMemberId(),
                request.contentType(),
                request.resourceType(),
                request.audience(),
                request.title(),
                request.content(),
                fileId,
                request.version(),
                request.pinned(),
                now
        );

        try {
            return EventContentDtos.Summary.from(
                    eventContentRepository.save(content), uploadedAsset, presignedUrl(uploadedAsset));
        } catch (Exception e) {
            // 트랜잭션 롤백 시 S3 고아 파일 보상 삭제
            if (uploadedStorageKey != null) {
                try {
                    fileStorageService.delete(uploadedStorageKey);
                } catch (Exception deleteEx) {
                    log.warn("S3 보상 삭제 실패. fileId={}", fileId, deleteEx);
                }
            }
            throw e;
        }
    }

    /**
     * 공지·자료 수정 (CONTENT-API-004)
     * EVENT_MANAGER 또는 PLATFORM_ADMIN만 수정 가능
     * 새 파일이 있으면 교체 후 기존 파일 S3·DB 삭제, 없으면 기존 fileId 유지
     *
     * @param contentId 콘텐츠 ID
     * @param request   수정 요청 DTO
     * @param file      새 첨부파일 (없으면 기존 유지)
     * @param member    인증 회원
     */
    @Transactional
    public EventContentDtos.Summary updateContent(
            Long contentId,
            EventContentDtos.UpdateRequest request,
            MultipartFile file,
            AuthenticatedMemberDto member) {

        // 비로그인 요청 401 처리
        requireAuthenticated(member);

        // 콘텐츠 조회
        EventContent content = eventContentRepository.findById(contentId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // EVENT_MANAGER 또는 PLATFORM_ADMIN만 수정 가능
        if (member.getPlatformRole() != PlatformRole.PLATFORM_ADMIN
                && !eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                content.getEventId(), member.getMemberId(), EventRole.EVENT_MANAGER)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        // 새 파일이 있으면 업로드 후 fileId 교체, 없으면 기존 fileId 유지
        Long oldFileId = content.getFileId();
        Long fileId = oldFileId;
        FileAsset newAsset = null;
        String newStorageKey = null;

        if (file != null && !file.isEmpty()) {
            fileId = fileService.upload(file, FileAccessLevel.PRIVATE, member.getMemberId()).getFileId();
            newAsset = fileAssetRepository.findById(fileId).orElse(null);
            newStorageKey = newAsset != null ? newAsset.getStorageKey() : null;
        }

        try {
            content.update(
                    request.contentType(),
                    request.resourceType(),
                    request.audience(),
                    request.title(),
                    request.content(),
                    fileId,
                    request.version(),
                    request.pinned(),
                    OffsetDateTime.now()
            );

            // 응답용 파일 메타: 새 파일이면 newAsset, 기존 유지면 기존 파일 조회
            FileAsset resultAsset = newAsset != null
                    ? newAsset
                    : (fileId != null ? fileAssetRepository.findById(fileId).orElse(null) : null);

            EventContentDtos.Summary result =
                    EventContentDtos.Summary.from(content, resultAsset, presignedUrl(resultAsset));

            // 기존 파일 삭제는 커밋 이후로 미룬다 (EventContentFileCleanupListener)
            // 커밋 전에 지우면 이후 롤백 시 S3 객체가 복구되지 않아
            // 되살아난 콘텐츠가 다운로드 불가능한 파일을 참조하게 된다
            if (newStorageKey != null && oldFileId != null) {
                applicationEventPublisher.publishEvent(
                        new EventContentFileReplaced(content.getId(), oldFileId));
            }

            return result;

        } catch (Exception e) {
            // 롤백 시 새로 업로드한 S3 파일 보상 삭제
            if (newStorageKey != null) {
                try {
                    fileStorageService.delete(newStorageKey);
                } catch (Exception deleteEx) {
                    log.warn("S3 보상 삭제 실패. fileId={}", fileId, deleteEx);
                }
            }
            throw e;
        }
    }

    /**
     * 공지·자료 삭제 (CONTENT-API-005)
     * EVENT_MANAGER 또는 PLATFORM_ADMIN만 삭제 가능
     * 연결된 파일을 S3·DB에서 함께 삭제
     *
     * @param contentId 콘텐츠 ID
     * @param member    인증 회원
     */
    @Transactional
    public void deleteContent(Long contentId, AuthenticatedMemberDto member) {

        // 비로그인 요청 401 처리
        requireAuthenticated(member);

        // 콘텐츠 조회
        EventContent content = eventContentRepository.findById(contentId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // EVENT_MANAGER 또는 PLATFORM_ADMIN만 삭제 가능
        if (member.getPlatformRole() != PlatformRole.PLATFORM_ADMIN
                && !eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                        content.getEventId(), member.getMemberId(), EventRole.EVENT_MANAGER)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        Long fileId = content.getFileId();
        eventContentRepository.delete(content);

        // 연결된 파일 S3·DB에서 삭제
        if (fileId != null) {
            fileAssetRepository.findById(fileId).ifPresent(asset -> {
                try {
                    fileStorageService.delete(asset.getStorageKey());
                } catch (Exception e) {
                    log.warn("콘텐츠 삭제 후 S3 파일 삭제 실패. fileId={}", fileId, e);
                }
                fileAssetRepository.delete(asset);
            });
        }
    }

    /**
     * 비로그인 요청을 401로 처리하는 헬퍼
     * 쓰기 작업(등록·수정·삭제) 시작 시 호출
     */
    private void requireAuthenticated(AuthenticatedMemberDto member) {
        if (member == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * 권한에 따른 조회 가능 audience 목록 결정 (CONTENT-003)
     * - 비로그인: ALL만
     * - PLATFORM_ADMIN / 해당 행사 EVENT_MANAGER: 전체 audience (ALL, EXHIBITOR, VISITOR)
     * - 그 외 일반 회원: ALL + 아래 판정을 통과한 audience
     *   - EXHIBITOR: 소속 조직이 해당 행사에서 부스를 배정받은 경우 ({@link #isExhibitor})
     *   - VISITOR: 해당 행사의 유효한 입장권을 보유한 경우 ({@link #isVisitor})
     *
     * 목록 조회·상세 조회가 모두 이 메서드를 사용하므로,
     * 접근 정책을 바꿀 때는 여기만 수정하면 두 경로에 함께 적용된다.
     */
    private List<EventContentAudience> resolveAllowedAudiences(
            Long eventId, AuthenticatedMemberDto member) {
        // 비로그인 사용자는 전체 공개 콘텐츠만 볼 수 있다
        if (member == null) {
            return List.of(EventContentAudience.ALL);
        }
        if (member.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return Arrays.asList(EventContentAudience.values());
        }
        if (eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                eventId, member.getMemberId(), EventRole.EVENT_MANAGER)) {
            return Arrays.asList(EventContentAudience.values());
        }

        // CONTENT-003: 전체 공개에 더해, 참가기업·관람객에 해당하면 각 대상 콘텐츠도 볼 수 있다
        List<EventContentAudience> allowed = new ArrayList<>();
        allowed.add(EventContentAudience.ALL);
        if (isExhibitor(eventId, member.getMemberId())) {
            allowed.add(EventContentAudience.EXHIBITOR);
        }
        if (isVisitor(eventId, member.getMemberId())) {
            allowed.add(EventContentAudience.VISITOR);
        }
        return allowed;
    }

    /**
     * 참가기업 판정 (CONTENT-003)
     * 회원이 활성 상태로 소속된 조직 중 하나가 해당 행사에서 부스를 배정받았으면 참가기업으로 본다.
     * 조직 내 역할(OWNER/MANAGER/STAFF)은 구분하지 않는다 — 자료 열람은 소속 구성원 전체에 필요하다.
     */
    private boolean isExhibitor(Long eventId, Long memberId) {
        List<Long> organizationIds = eventOrganizationMemberRepository
                .findAllByMemberIdAndStatus(memberId, OrganizationMemberStatus.ACTIVE)
                .stream()
                .map(OrganizationMember::getOrganizationId)
                .distinct()
                .toList();
        if (organizationIds.isEmpty()) {
            return false;
        }
        return eventBoothAssignmentRepository
                .existsByEventIdAndAssignedOrganizationIdIn(eventId, organizationIds);
    }

    /**
     * 관람객 판정 (CONTENT-003)
     * 해당 행사의 유효한 입장권(발급·사용 완료)을 보유하면 관람객으로 본다.
     */
    private boolean isVisitor(Long eventId, Long memberId) {
        return eventAdmissionTicketRepository
                .existsEventAdmissionTicket(eventId, memberId, VALID_TICKET_STATUSES);
    }
}
