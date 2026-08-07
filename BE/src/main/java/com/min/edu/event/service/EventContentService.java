package com.min.edu.event.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.EventContent;
import com.min.edu.event.domain.EventContentAudience;
import com.min.edu.event.domain.EventContentType;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.dto.EventContentDtos;
import com.min.edu.event.repository.EventContentRepository;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.file.domain.FileAccessLevel;
import com.min.edu.file.domain.FileAsset;
import com.min.edu.file.repository.FileAssetRepository;
import com.min.edu.file.service.FileService;
import com.min.edu.file.storage.FileStorageService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final EventContentRepository eventContentRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final FileService fileService;
    private final FileAssetRepository fileAssetRepository;
    private final FileStorageService fileStorageService;

    /**
     * 공지·자료 목록 조회
     *
     * 권한별 audience 필터링:
     * - PLATFORM_ADMIN 또는 해당 행사 EVENT_MANAGER → ALL / EXHIBITOR / VISITOR 전체 조회
     * - 그 외 (일반 회원·비로그인) → ALL audience만 조회
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

        return eventContentRepository.findAll(spec, sort)
                .stream()
                .map(EventContentDtos.Summary::from)
                .toList();
    }

    /**
     * 공지·자료 상세 조회
     *
     * 권한별 audience 접근 제어:
     * - PLATFORM_ADMIN / 해당 행사 EVENT_MANAGER → audience 제한 없음
     * - 그 외 → ALL audience 콘텐츠만 접근 가능 (EXHIBITOR·VISITOR는 403)
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

        return EventContentDtos.Summary.from(content);
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

        // 파일이 있으면 업로드 후 fileId 획득 (롤백 보상을 위해 storageKey 보관)
        Long fileId = null;
        String uploadedStorageKey = null;
        if (file != null && !file.isEmpty()) {
            fileId = fileService.upload(file, FileAccessLevel.PRIVATE, member.getMemberId()).getFileId();
            uploadedStorageKey = fileAssetRepository.findById(fileId)
                    .map(FileAsset::getStorageKey).orElse(null);
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
            return EventContentDtos.Summary.from(eventContentRepository.save(content));
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
        String newStorageKey = null;

        if (file != null && !file.isEmpty()) {
            fileId = fileService.upload(file, FileAccessLevel.PRIVATE, member.getMemberId()).getFileId();
            newStorageKey = fileAssetRepository.findById(fileId)
                    .map(FileAsset::getStorageKey).orElse(null);
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

            EventContentDtos.Summary result = EventContentDtos.Summary.from(content);

            // 파일 교체 완료 후 기존 파일 S3·DB에서 삭제
            if (newStorageKey != null && oldFileId != null) {
                fileAssetRepository.findById(oldFileId).ifPresent(old -> {
                    try {
                        fileStorageService.delete(old.getStorageKey());
                    } catch (Exception deleteEx) {
                        log.warn("기존 파일 S3 삭제 실패. fileId={}", oldFileId, deleteEx);
                    }
                    fileAssetRepository.delete(old);
                });
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
     * 권한에 따른 조회 가능 audience 목록 결정
     * - PLATFORM_ADMIN / 해당 행사 EVENT_MANAGER: 전체 audience (ALL, EXHIBITOR, VISITOR)
     * - 그 외: ALL만
     */
    private List<EventContentAudience> resolveAllowedAudiences(
            Long eventId, AuthenticatedMemberDto member) {
        if (member != null) {
            if (member.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
                return Arrays.asList(EventContentAudience.values());
            }
            if (eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                    eventId, member.getMemberId(), EventRole.EVENT_MANAGER)) {
                return Arrays.asList(EventContentAudience.values());
            }
        }
        return List.of(EventContentAudience.ALL);
    }
}
