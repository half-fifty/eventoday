package com.min.edu.admin.service;

import com.min.edu.admin.domain.PlatformNotice;
import com.min.edu.admin.dto.PlatformAdminDtos;
import com.min.edu.admin.repository.PlatformNoticeRepository;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.html.HtmlSanitizer;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼(사이트 전체) 공지 서비스
 *
 * 목록·상세 조회는 비로그인 사용자도 가능한 공개 API이고,
 * 등록·수정·삭제는 PLATFORM_ADMIN만 가능하다.
 * 권한 검증은 PlatformAdminService와 동일하게 서비스 레이어에서 수행한다.
 *
 * 본문은 리치 텍스트 에디터가 만든 HTML을 저장하므로 등록·수정 시 HtmlSanitizer로 정제한다.
 */
@Service
@Transactional(readOnly = true)
public class PlatformNoticeService {

    // 제목·본문 길이 상한 (platform_notices 컬럼 정의와 일치시킨다)
    private static final int MAX_TITLE_LENGTH = 200;

    // 공개 API이므로 페이지 크기를 제한한다 (EventContentService.listAllContents와 동일한 이유)
    private static final int MAX_PAGE_SIZE = 100;

    // 깊은 페이지는 DB가 앞의 행을 전부 건너뛰며 정렬해야 해서 비용이 크다.
    // 공개 API라 큰 page 값을 반복 호출당할 수 있으므로 상한을 둔다.
    private static final int MAX_PAGE_NUMBER = 10_000;

    private final PlatformNoticeRepository noticeRepository;
    private final PlatformAuditService auditService;

    public PlatformNoticeService(PlatformNoticeRepository noticeRepository,
            PlatformAuditService auditService) {
        this.noticeRepository = noticeRepository;
        this.auditService = auditService;
    }

    /**
     * 공지 목록 페이지 조회 (공개)
     *
     * 상단 고정 공지는 정렬 기준상 첫 페이지 위쪽에 모인다.
     * 검색어가 있으면 제목·본문에서 부분 일치로 찾는다.
     *
     * @param page    페이지 번호 (0 이상 MAX_PAGE_NUMBER 이하)
     * @param size    페이지 크기 (최대 MAX_PAGE_SIZE)
     * @param keyword 검색어 (null·공백이면 전체)
     */
    public PlatformAdminDtos.NoticePageResponse notices(int page, int size, String keyword) {
        // 페이지 파라미터 검증 (EventContentService.listAllContents 패턴)
        if (page < 0 || page > MAX_PAGE_NUMBER || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 정렬은 쿼리에 직접 적혀 있으므로 Pageable에 Sort를 담지 않는다 (PlatformNoticeRepository 참고)
        Pageable pageable = PageRequest.of(page, size);
        String trimmedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<PlatformNotice> result = noticeRepository.searchNotices(trimmedKeyword, pageable);

        return new PlatformAdminDtos.NoticePageResponse(
            result.getContent().stream().map(this::toDto).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.isFirst(),
            result.isLast(),
            result.isEmpty());
    }

    /** 공지 상세 조회 (공개) */
    public PlatformAdminDtos.Notice notice(Long noticeId) {
        return noticeRepository.findById(noticeId)
            .map(this::toDto)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
    }

    /** 공지 등록 (PLATFORM_ADMIN) */
    @Transactional
    public PlatformAdminDtos.Notice createNotice(
            PlatformAdminDtos.NoticeCreateRequest request, AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        String title = requireTitle(request == null ? null : request.title());
        OffsetDateTime now = OffsetDateTime.now();

        PlatformNotice notice = noticeRepository.save(PlatformNotice.create(
            actor.getMemberId(), title, normalizeContent(request.content()), request.pinned(), now));

        // 관리자센터 감사 로그 탭에 남기기 위해 기록한다 (계정·행사 처리와 동일한 방식)
        auditService.record(actor.getMemberId(), "NOTICE", "공지 등록",
            notice.getId(), notice.getTitle(), null);
        return toDto(notice);
    }

    /** 공지 수정 (PLATFORM_ADMIN) */
    @Transactional
    public PlatformAdminDtos.Notice updateNotice(Long noticeId,
            PlatformAdminDtos.NoticeUpdateRequest request, AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        String title = requireTitle(request == null ? null : request.title());
        PlatformNotice notice = noticeRepository.findById(noticeId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        notice.update(title, normalizeContent(request.content()), request.pinned(), OffsetDateTime.now());

        auditService.record(actor.getMemberId(), "NOTICE", "공지 수정",
            notice.getId(), notice.getTitle(), null);
        return toDto(notice);
    }

    /** 공지 삭제 (PLATFORM_ADMIN) */
    @Transactional
    public void deleteNotice(Long noticeId, AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        PlatformNotice notice = noticeRepository.findById(noticeId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 감사 로그에 제목을 남겨야 하므로 삭제 전에 기록한다
        auditService.record(actor.getMemberId(), "NOTICE", "공지 삭제",
            notice.getId(), notice.getTitle(), null);
        noticeRepository.delete(notice);
    }

    private PlatformAdminDtos.Notice toDto(PlatformNotice notice) {
        return new PlatformAdminDtos.Notice(notice.getId(), notice.getTitle(), notice.getContent(),
            notice.isPinned(), notice.getPublishedAt(), notice.getUpdatedAt());
    }

    /** 제목 필수·길이 검증 (요청 DTO에 검증 애노테이션을 두지 않는 admin 패키지 컨벤션을 따른다) */
    private String requireTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank() || rawTitle.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return rawTitle.trim();
    }

    /**
     * 본문 정제 — 리치 텍스트 에디터가 보낸 HTML에서 허용 태그·속성만 남긴다.
     *
     * 저장 시점에 정제해 두면 조회할 때마다 정제할 필요가 없고,
     * 프론트엔드를 거치지 않고 API를 직접 호출하는 요청도 함께 막힌다.
     * 내용이 없으면 null을 반환해 "본문 없음"을 한 가지 값으로 표현한다.
     */
    private String normalizeContent(String rawContent) {
        return HtmlSanitizer.sanitize(rawContent);
    }

    private void requireAdmin(AuthenticatedMemberDto actor) {
        if (actor == null || actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }
}
