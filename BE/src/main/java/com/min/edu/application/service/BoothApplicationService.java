package com.min.edu.application.service;

import com.min.edu.application.dto.BoothApplicationResponseDto;
import com.min.edu.application.dto.BoothApplicationSubmitRequestDto;
import com.min.edu.application.support.ApplicationNoGenerator;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothApplication;
import com.min.edu.booth.domain.BoothApplicationFile;
import com.min.edu.booth.domain.BoothApplicationFileType;
import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.booth.domain.BoothStatus;
import com.min.edu.booth.repository.BoothApplicationFileRepository;
import com.min.edu.booth.repository.BoothApplicationRepository;
import com.min.edu.booth.repository.BoothOrganizationMemberRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.file.repository.FileAssetRepository;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import com.min.edu.recruitment.repository.BoothRecruitmentRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoothApplicationService {

    private final BoothRecruitmentRepository boothRecruitmentRepository;
    private final BoothRepository boothRepository;
    private final BoothApplicationRepository boothApplicationRepository;
    private final BoothApplicationFileRepository boothApplicationFileRepository;
    private final BoothOrganizationMemberRepository boothOrganizationMemberRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ApplicationNoGenerator applicationNoGenerator;

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
        validateEstimateFile(request.getEstimateFileId());

        // 기타 파일 존재 검증
        List<Long> otherFileIds = request.getOtherFileIds() != null ? request.getOtherFileIds() : List.of();
        validateOtherFiles(otherFileIds);

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

        // 신청 파일 연결 (견적서 + 기타 파일)
        List<BoothApplicationFile> files = buildApplicationFiles(application.getId(), request.getEstimateFileId(), otherFileIds, now);
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

    /** 견적서 파일이 실제로 존재하는지 검증 */
    private void validateEstimateFile(Long estimateFileId) {
        if (!fileAssetRepository.existsById(estimateFileId)) {
            throw new BusinessException(GlobalErrorCode.FILE_NOT_FOUND);
        }
    }

    /** 기타 첨부 파일들이 실제로 존재하는지 검증 */
    private void validateOtherFiles(List<Long> otherFileIds) {
        for (Long fileId : otherFileIds) {
            if (!fileAssetRepository.existsById(fileId)) {
                throw new BusinessException(GlobalErrorCode.FILE_NOT_FOUND);
            }
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