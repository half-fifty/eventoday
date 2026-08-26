package com.min.edu.event.repository;

import com.min.edu.booth.domain.Booth;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공지·자료 공개 대상(CONTENT-003) 판정 중 "참가기업" 여부를 확인하기 위한 조회 전용 리포지토리.
 *
 * 부스 도메인 엔티티를 참조하지만 콘텐츠 접근 제어 목적의 조회만 사용하므로,
 * BoothOrganizationMemberRepository / EventOrganizationMemberRepository와 같이
 * 사용하는 패키지에서 별도 인터페이스를 두는 기존 방식을 따른다.
 */
public interface EventBoothAssignmentRepository extends JpaRepository<Booth, Long> {

    /** 해당 행사에서 주어진 조직들 중 하나라도 부스를 배정받았는지 */
    boolean existsByEventIdAndAssignedOrganizationIdIn(
            Long eventId, Collection<Long> organizationIds);
}
