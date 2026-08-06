package com.min.edu.application.service;

import com.min.edu.application.dto.BoothApplicationPageResponse;
import com.min.edu.application.dto.BoothApplicationResponseDto;
import com.min.edu.application.dto.BoothApplicationSubmitRequestDto;
import com.min.edu.application.dto.BoothApplicationRejectRequestDto;
import com.min.edu.application.dto.BoothApplicationFileResponseDto;
import com.min.edu.application.support.ApplicationNoGenerator;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothApplication;
import com.min.edu.booth.domain.BoothApplicationFile;
import com.min.edu.booth.domain.BoothApplicationFileType;
import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.booth.domain.BoothApplicationStatus;
import com.min.edu.booth.domain.BoothStatus;
import com.min.edu.booth.repository.BoothApplicationFileRepository;
import com.min.edu.booth.repository.BoothApplicationRepository;
import com.min.edu.booth.repository.BoothOrganizationMemberRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.file.repository.FileAssetRepository;
import com.min.edu.file.domain.FileAsset;
import com.min.edu.file.storage.FileStorageService;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import com.min.edu.recruitment.repository.BoothRecruitmentRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoothApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BoothRecruitmentRepository boothRecruitmentRepository;
    private final BoothRepository boothRepository;
    private final BoothApplicationRepository boothApplicationRepository;
    private final BoothApplicationFileRepository boothApplicationFileRepository;
    private final BoothOrganizationMemberRepository boothOrganizationMemberRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ApplicationNoGenerator applicationNoGenerator;
    private final EventMemberRepository eventMemberRepository;
    private final EventRepository eventRepository;
    private final FileStorageService fileStorageService;

    /**
     * 부스 신청 제출
     */
    @Transactional
    public BoothApplicationResponseDto submit(
            Long recruitmentId,
            BoothApplicationSubmitRequestDto request,
            AuthenticatedMemberDto member) {

        OffsetDateTime now = OffsetDateTime.now();

        // 모집 공고 조회 및 OPEN 상태 검증
        BoothRecruitment recruitment = boothRecruitmentRepository.findById(recruitmentId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        validateRecruitmentOpen(recruitment, now);

        // 신청자가 해당 조직의 OWNER 또는 MANAGER인지 검증
        requireOrganizationManager(request.getApplicantOrganizationId(), member.getMemberId());

        // 부스 조회 (비관적 락 - 동시 신청 충돌 방지)
        Booth booth = boothRepository.findByIdWithLock(request.getBoothId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 부스 AVAILABLE 상태 확인
        if (booth.getStatus() != BoothStatus.AVAILABLE) {
            throw new BusinessException(GlobalErrorCode.BOOTH_NOT_AVAILABLE);
        }

        // 설비 조건 충족 여부 확인
        validateBoothRequirements(booth, request);

        // 견적서 파일 존재 검증
        validateEstimateFile(request.getEstimateFileId(), member.getMemberId());

        // 기타 파일 존재 검증
        List<Long> otherFileIds = request.getOtherFileIds() != null
                ? request.getOtherFileIds()
                : List.of();
        validateOtherFiles(otherFileIds, member.getMemberId());

        // 부스 상태 → APPLICATION_PENDING으로 변경
        booth.markAsPending(now);

        // 신청서 생성
        String applicationNo = applicationNoGenerator.generate();
        BoothApplication application = BoothApplication.create(
                applicationNo,
                recruitmentId,
                booth.getId(),
                request.getApplicantOrganizationId(),
                member.getMemberId(),
                request.getTeamName(),
                request.getContactName(),
                request.getContactEmail(),
                request.getContactPhone(),
                request.getActivityDescription(),
                request.getExhibitionContent(),
                request.getExpectedVisitors(),
                request.getElectricityRequired(),
                request.getWaterRequired(),
                request.getDrainageRequired(),
                request.getInternetRequired(),
                request.getApplicationReason(),
                now
        );
        boothApplicationRepository.save(application);

        // 중복 제거 후 파일 연결
        List<Long> uniqueOtherFileIds = otherFileIds.stream().distinct().toList();
        // 신청 파일 연결 (견적서 + 기타 파일)
        List<BoothApplicationFile> files = buildApplicationFiles(
                application.getId(), request.getEstimateFileId(), uniqueOtherFileIds, now);
        boothApplicationFileRepository.saveAll(files);

        return BoothApplicationResponseDto.from(application);
    }

    /**
     * 참가기업 신청 목록 조회
     * ORG_MEMBER: OWNER/MANAGER/STAFF 모두 조회 가능
     */
    @Transactional(readOnly = true)
    public List<BoothApplicationResponseDto> listByOrganization(
            Long organizationId,
            AuthenticatedMemberDto member) {

        // 해당 조직의 ACTIVE 멤버인지 검증 (역할 무관)
        requireOrganizationMember(organizationId, member.getMemberId());

        return boothApplicationRepository
                .findAllByApplicantOrganizationIdOrderBySubmittedAtDesc(organizationId)
                .stream()
                .map(BoothApplicationResponseDto::from)
                .toList();
    }

    /**
     * 신청 상세 조회
     * - ORG_MEMBER: 본인 조직의 신청만 조회 가능
     * - EVENT_MANAGER: 해당 모집공고 행사의 관리자면 조회 가능
     * - PLATFORM_ADMIN: 모두 조회 가능
     */
    @Transactional(readOnly = true)
    public BoothApplicationResponseDto getDetail(Long applicationId, AuthenticatedMemberDto member) {

        BoothApplication application = boothApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // PLATFORM_ADMIN은 모두 접근 가능
        if (member.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return BoothApplicationResponseDto.from(application);
        }

        // 본인 조직 신청이면 조직 멤버 여부 검증 후 반환
        if (isOrganizationMember(application.getApplicantOrganizationId(), member.getMemberId())) {
            return BoothApplicationResponseDto.from(application);
        }

        // 해당 모집공고가 속한 행사의 EVENT_MANAGER인지 검증
        if (isEventManagerOfApplication(application, member.getMemberId())) {
            return BoothApplicationResponseDto.from(application);
        }

        throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }

    /**
     * 신청 취소
     */
    @Transactional
    public void cancel(Long applicationId, AuthenticatedMemberDto member) {

        BoothApplication application = boothApplicationRepository.findByIdWithLock(applicationId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 본인 조직의 신청인지 검증 (ORG_MEMBER)
        requireOrganizationMember(application.getApplicantOrganizationId(), member.getMemberId());

        // SUBMITTED 상태에서만 취소 가능 (검토 시작 이전)
        if (application.getStatus() != BoothApplicationStatus.SUBMITTED) {
            throw new BusinessException(GlobalErrorCode.APPLICATION_CANNOT_BE_CANCELLED);
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 신청 취소
        application.cancel(now);

        // 부스 상태 AVAILABLE로 복원 (비관적 락으로 안전하게 복원)
        Booth booth = boothRepository.findByIdWithLock(application.getBoothId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        booth.markAsAvailable(now);

        log.info("부스 신청 취소 - applicationId: {}, boothId: {}, memberId: {}",
                applicationId, booth.getId(), member.getMemberId());
    }

    /**
     * 검토 시작 (APP-API-006)
     * - SUBMITTED 상태에서만 UNDER_REVIEW로 전환 가능
     * - EVENT_MANAGER / PLATFORM_ADMIN만 접근 가능
     */
    @Transactional
    public void startReview(Long applicationId, AuthenticatedMemberDto member) {

        BoothApplication application = boothApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 해당 신청이 속한 행사의 EVENT_MANAGER인지 검증
        requireEventManagerOfApplication(application, member);

        // SUBMITTED 상태에서만 검토 시작 가능
        if (application.getStatus() != BoothApplicationStatus.SUBMITTED) {
            throw new BusinessException(GlobalErrorCode.APPLICATION_REVIEW_NOT_ALLOWED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        application.startReview(member.getMemberId(), now);
    }

    /**
     * 신청 승인·부스 배정
     * - UNDER_REVIEW 상태에서만 APPROVED로 전환 가능
     * - 부스 상태: APPLICATION_PENDING → ASSIGNED, assignedOrganizationId 기록
     * - EVENT_MANAGER / PLATFORM_ADMIN만 접근 가능
     * - 신청서와 부스 변경을 하나의 트랜잭션으로 처리
     */
    @Transactional
    public void approve(Long applicationId, AuthenticatedMemberDto member) {

        // 비관적 락으로 신청 조회 (동시 처리 방지)
        BoothApplication application = boothApplicationRepository.findByIdWithLock(applicationId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 해당 신청이 속한 행사의 EVENT_MANAGER인지 검증
        requireEventManagerOfApplication(application, member);

        // UNDER_REVIEW 상태에서만 승인 가능
        if (application.getStatus() != BoothApplicationStatus.UNDER_REVIEW) {
            throw new BusinessException(GlobalErrorCode.APPLICATION_APPROVE_NOT_ALLOWED);
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 신청 승인 처리
        application.approve(member.getMemberId(), now);

        // 부스 상태 ASSIGNED로 변경 + 배정 조직 기록 (비관적 락)
        Booth booth = boothRepository.findByIdWithLock(application.getBoothId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        booth.markAsAssigned(application.getApplicantOrganizationId(), now);

        log.info("부스 신청 승인 - applicationId: {}, boothId: {}, organizationId: {}, reviewedBy: {}",
                applicationId, booth.getId(), application.getApplicantOrganizationId(), member.getMemberId());
    }

    /**
     * 신청 반려·부스 복원
     * - UNDER_REVIEW 상태에서만 REJECTED로 전환 가능
     * - 부스 상태: APPLICATION_PENDING → AVAILABLE 복원
     * - EVENT_MANAGER / PLATFORM_ADMIN만 접근 가능
     * - 신청서 반려와 부스 복원을 하나의 트랜잭션으로 처리
     */
    @Transactional
    public void reject(Long applicationId, BoothApplicationRejectRequestDto request, AuthenticatedMemberDto member) {

        // 비관적 락으로 신청 조회 (동시 처리 방지)
        BoothApplication application = boothApplicationRepository.findByIdWithLock(applicationId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 해당 신청이 속한 행사의 EVENT_MANAGER인지 검증
        requireEventManagerOfApplication(application, member);

        // UNDER_REVIEW 상태에서만 반려 가능
        if (application.getStatus() != BoothApplicationStatus.UNDER_REVIEW) {
            throw new BusinessException(GlobalErrorCode.APPLICATION_REJECT_NOT_ALLOWED);
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 신청 반려 처리
        application.reject(member.getMemberId(), request.getRejectionReason(), now);

        // 부스 상태 AVAILABLE로 복원 (비관적 락)
        Booth booth = boothRepository.findByIdWithLock(application.getBoothId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        booth.markAsAvailable(now);

        log.info("부스 신청 반려 - applicationId: {}, boothId: {}, reviewedBy: {}",
                applicationId, booth.getId(), member.getMemberId());
    }

    /**
     * 신청 첨부파일 목록 조회
     * - ORG_MEMBER: 본인 조직의 신청만 조회 가능
     * - EVENT_MANAGER: 해당 행사 신청 조회 가능
     * - PLATFORM_ADMIN: 모두 조회 가능
     * - PRIVATE 파일도 Presigned URL로 접근 가능하도록 FileStorageService 직접 사용
     */
    @Transactional(readOnly = true)
    public List<BoothApplicationFileResponseDto> listFiles(Long applicationId, AuthenticatedMemberDto member) {

        BoothApplication application = boothApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // getDetail()과 동일한 권한 검증 패턴
        if (member.getPlatformRole() != PlatformRole.PLATFORM_ADMIN
                && !isOrganizationMember(application.getApplicantOrganizationId(), member.getMemberId())
                && !isEventManagerOfApplication(application, member.getMemberId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        // 신청에 연결된 파일 목록 조회
        List<BoothApplicationFile> appFiles =
                boothApplicationFileRepository.findAllByBoothApplicationId(applicationId);

        // fileId 목록으로 FileAsset 일괄 조회 후 Map으로 변환
        List<Long> fileIds = appFiles.stream()
                .map(BoothApplicationFile::getFileId)
                .toList();

        Map<Long, FileAsset> fileAssetMap = fileAssetRepository.findAllById(fileIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(FileAsset::getId, f -> f));

        // Presigned URL 생성 후 응답 DTO 조립
        return appFiles.stream()
                .filter(f -> fileAssetMap.containsKey(f.getFileId()))
                .map(f -> {
                    FileAsset asset = fileAssetMap.get(f.getFileId());
                    String downloadUrl = fileStorageService.generatePresignedUrl(asset.getStorageKey());
                    return BoothApplicationFileResponseDto.of(f.getFileType(), asset, downloadUrl);
                })
                .toList();
    }

    /** 신청에 연결된 행사의 EVENT_MANAGER 권한 검증 (예외 발생형) */
    private void requireEventManagerOfApplication(BoothApplication application, AuthenticatedMemberDto member) {
        if (member.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return;
        }
        boolean isManager = boothRecruitmentRepository.findById(application.getRecruitmentId())
                .map(recruitment -> eventMemberRepository
                        .existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                                recruitment.getEventId(),
                                member.getMemberId(),
                                EventRole.EVENT_MANAGER
                        ))
                .orElse(false);
        if (!isManager) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    /**
     * 행사 신청 목록·검색
     * - EVENT_MANAGER / PLATFORM_ADMIN만 접근 가능
     * - Specification으로 동적 쿼리 구성 (null 파라미터는 조건에서 제외하여 PostgreSQL 타입 추론 오류 방지)
     */
    @Transactional(readOnly = true)
    public BoothApplicationPageResponse listByEvent(
            Long eventId,
            BoothApplicationStatus status,
            String teamName,
            String boothCode,
            OffsetDateTime submittedFrom,
            OffsetDateTime submittedTo,
            int page,
            int size,
            AuthenticatedMemberDto member) {

        if (!eventRepository.existsById(eventId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        requireEventManager(eventId, member);
        validatePageRequest(page, size);

        // 행사에 속한 모집공고 ID 조회
        List<Long> recruitmentIds = boothRecruitmentRepository.findIdsByEventId(eventId);
        if (recruitmentIds.isEmpty()) {
            return new BoothApplicationPageResponse(List.of(), page, size, 0, 0, true, true, true);
        }

        // boothCode 필터가 있으면 boothId 목록으로 변환
        final List<Long> boothIds;
        if (boothCode != null && !boothCode.isBlank()) {
            List<Long> ids = boothRepository.findIdsByBoothCodeLike(normalizeKeyword(boothCode));
            if (ids.isEmpty()) {
                return new BoothApplicationPageResponse(List.of(), page, size, 0, 0, true, true, true);
            }
            boothIds = ids;
        } else {
            boothIds = null;
        }

        final String normalizedTeamName = normalizeKeyword(teamName);

        // null인 조건은 쿼리에 포함하지 않음 (PostgreSQL 파라미터 타입 추론 오류 방지)
        Specification<BoothApplication> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 행사 소속 신청만 (recruitmentId IN 모집공고 목록)
            predicates.add(root.get("recruitmentId").in(recruitmentIds));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (normalizedTeamName != null) {
                predicates.add(cb.like(cb.lower(root.get("teamName")), normalizedTeamName, '!'));
            }
            if (boothIds != null) {
                predicates.add(root.get("boothId").in(boothIds));
            }
            if (submittedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), submittedFrom));
            }
            if (submittedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), submittedTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<BoothApplication> result = boothApplicationRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt")));

        return new BoothApplicationPageResponse(
                result.getContent().stream().map(BoothApplicationResponseDto::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                result.isEmpty()
        );
    }

    /** EVENT_MANAGER 또는 PLATFORM_ADMIN 권한 검증 */
    private void requireEventManager(Long eventId, AuthenticatedMemberDto member) {
        if (member.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return;
        }
        boolean isEventManager = eventMemberRepository
                .existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                        eventId, member.getMemberId(), EventRole.EVENT_MANAGER);
        if (!isEventManager) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    /** 페이지 요청 크기 상한 검증 */
    private void validatePageRequest(int page, int size) {
        if (size > MAX_PAGE_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /** LIKE 검색용 키워드 정규화 - 소문자 변환 및 % 래핑 */
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    /** 조직 소속 여부 확인 (권한 예외 없이 boolean 반환) */
    private boolean isOrganizationMember(Long organizationId, Long memberId) {
        return boothOrganizationMemberRepository
                .existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                        organizationId,
                        memberId,
                        OrganizationMemberStatus.ACTIVE,
                        List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER, OrganizationRole.STAFF)
                );
    }

    /** 신청에 연결된 모집공고의 행사 EVENT_MANAGER인지 확인 */
    private boolean isEventManagerOfApplication(BoothApplication application, Long memberId) {
        return boothRecruitmentRepository.findById(application.getRecruitmentId())
                .map(recruitment -> eventMemberRepository
                        .existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                                recruitment.getEventId(),
                                memberId,
                                EventRole.EVENT_MANAGER
                        ))
                .orElse(false);
    }

    /**
     * 조직 소속 멤버 여부 검증 (OWNER/MANAGER/STAFF 모두 허용)
     * requireOrganizationManager()와 구분 - ORG_MEMBER는 역할 제한 없음
     */
    private void requireOrganizationMember(Long organizationId, Long memberId) {
        boolean isMember = boothOrganizationMemberRepository
                .existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                        organizationId,
                        memberId,
                        OrganizationMemberStatus.ACTIVE,
                        List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER, OrganizationRole.STAFF)
                );

        if (!isMember) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    /** 모집 공고가 OPEN 상태이고 현재 모집 기간인지 검증 */
    private void validateRecruitmentOpen(BoothRecruitment recruitment, OffsetDateTime now) {
        if (recruitment.getStatus() != BoothRecruitmentStatus.OPEN) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_NOT_OPEN);
        }

        if (now.isBefore(recruitment.getRecruitmentStartAt())
                || now.isAfter(recruitment.getRecruitmentEndAt())) {
            throw new BusinessException(GlobalErrorCode.RECRUITMENT_NOT_OPEN);
        }
    }

    /**
     * 신청자가 해당 조직의 OWNER 또는 MANAGER인지 검증
     * BoothOrganizationMemberRepository 활용 (팀원 신규 추가)
     */
    private void requireOrganizationManager(Long organizationId, Long memberId) {
        boolean isManager = boothOrganizationMemberRepository
                .existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                        organizationId,
                        memberId,
                        OrganizationMemberStatus.ACTIVE,
                        List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER)
                );

        if (!isManager) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    /** 신청 설비 조건이 부스에서 제공 가능한지 확인 */
    private void validateBoothRequirements(Booth booth, BoothApplicationSubmitRequestDto request) {
        if (request.getElectricityRequired() && !booth.isElectricityAvailable()) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REQUIREMENT_NOT_MET);
        }
        if (request.getWaterRequired() && !booth.isWaterAvailable()) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REQUIREMENT_NOT_MET);
        }
        if (request.getDrainageRequired() && !booth.isDrainageAvailable()) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REQUIREMENT_NOT_MET);
        }
        if (request.getInternetRequired() && !booth.isInternetAvailable()) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REQUIREMENT_NOT_MET);
        }
    }

    /** 견적서 파일이 실제로 존재하고 요청자 소유인지 검증 */
    private void validateEstimateFile(Long estimateFileId, Long memberId) {
        if (!fileAssetRepository.existsByIdAndUploadedBy(estimateFileId, memberId)) {
            throw new BusinessException(GlobalErrorCode.FILE_NOT_FOUND);
        }
    }

    /** 기타 첨부 파일들이 존재하고 요청자 소유인지 검증 (중복 제거 후 일괄 조회) */
    private void validateOtherFiles(List<Long> otherFileIds, Long memberId) {
        if (otherFileIds.isEmpty()) {
            return;
        }
        List<Long> uniqueIds = otherFileIds.stream().distinct().toList();
        long foundCount = fileAssetRepository.countByIdInAndUploadedBy(uniqueIds, memberId);
        if (foundCount != uniqueIds.size()) {
            throw new BusinessException(GlobalErrorCode.FILE_NOT_FOUND);
        }
    }

    /** 신청 파일 엔티티 목록 생성 (견적서 1개 + 기타 N개) */
    private List<BoothApplicationFile> buildApplicationFiles(
            Long applicationId, Long estimateFileId, List<Long> otherFileIds, OffsetDateTime now) {

        List<BoothApplicationFile> files = new ArrayList<>();

        // 견적서 파일
        files.add(BoothApplicationFile.builder()
                .boothApplicationId(applicationId)
                .fileId(estimateFileId)
                .fileType(BoothApplicationFileType.ESTIMATE)
                .createdAt(now)
                .build());

        // 기타 파일
        for (Long fileId : otherFileIds) {
            files.add(BoothApplicationFile.builder()
                    .boothApplicationId(applicationId)
                    .fileId(fileId)
                    .fileType(BoothApplicationFileType.OTHER)
                    .createdAt(now)
                    .build());
        }

        return files;
    }
}