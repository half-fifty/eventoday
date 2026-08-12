package com.min.edu.admin.service;

import com.min.edu.admin.domain.PlatformNotice;
import com.min.edu.admin.dto.PlatformAdminDtos;
import com.min.edu.admin.repository.PlatformNoticeRepository;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼(사이트 전체) 공지 서비스
 *
 * 목록 조회는 비로그인 사용자도 가능한 공개 API이고,
 * 등록·수정·삭제는 PLATFORM_ADMIN만 가능하다.
 * 권한 검증은 PlatformAdminService와 동일하게 서비스 레이어에서 수행한다.
 */
@Service
@Transactional(readOnly = true)
public class PlatformNoticeService {

    // 제목·본문 길이 상한 (platform_notices 컬럼 정의와 일치시킨다)
    private static final int MAX_TITLE_LENGTH = 200;

    private final PlatformNoticeRepository noticeRepository;
    private final PlatformAuditService auditService;

    public PlatformNoticeService(PlatformNoticeRepository noticeRepository,
            PlatformAuditService auditService) {
        this.noticeRepository = noticeRepository;
        this.auditService = auditService;
    }

    /** 공지 목록 조회 (공개) — 상단 고정 우선, 최신순 */
    public List<PlatformAdminDtos.Notice> notices() {
        return noticeRepository.findAllByOrderByPinnedDescPublishedAtDescIdDesc().stream()
            .map(this::toDto).toList();
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

    /** 빈 문자열은 null로 저장해 "본문 없음"을 한 가지 값으로 표현한다 */
    private String normalizeContent(String rawContent) {
        return (rawContent == null || rawContent.isBlank()) ? null : rawContent.trim();
    }

    private void requireAdmin(AuthenticatedMemberDto actor) {
        if (actor == null || actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }
}
