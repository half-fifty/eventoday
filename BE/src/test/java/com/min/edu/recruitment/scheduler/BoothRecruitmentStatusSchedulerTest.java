package com.min.edu.recruitment.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.organization.domain.Organization;
import com.min.edu.organization.domain.OrganizationStatus;
import com.min.edu.organization.domain.OrganizationType;
import com.min.edu.organization.repository.OrganizationRepository;
import com.min.edu.recruitment.repository.BoothRecruitmentRepository;

import jakarta.persistence.EntityManager;

// 스케줄러가 REQUIRES_NEW로 별도 트랜잭션을 열기 때문에, 테스트 트랜잭션에 롤백을 맡기면
// 스케줄러가 아직 커밋되지 않은(테스트에서 저장한) 데이터를 보지 못한다.
// 그래서 이 테스트는 @Transactional을 쓰지 않고 각 테스트 후 직접 데이터를 정리한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BoothRecruitmentStatusSchedulerTest {

    @Autowired
    private BoothRecruitmentStatusScheduler scheduler;

    @Autowired
    private BoothRecruitmentRepository boothRecruitmentRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EntityManager entityManager;

    private Long eventId;
    private Long organizationId;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();

        Organization organization = Organization.builder()
            .organizationType(OrganizationType.ORGANIZER)
            .name("테스트기획사")
            .contactEmail("org@example.com")
            .contactPhone("010-0000-0000")
            .status(OrganizationStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();
        organizationRepository.save(organization);
        organizationId = organization.getId();

        Event event = Event.builder()
            .organizerOrganizationId(organization.getId())
            .name("테스트 박람회")
            .eventType("EXHIBITION")
            .description("설명")
            .venueName("테스트홀")
            .address("서울시 어딘가")
            .startAt(now.plusDays(30))
            .endAt(now.plusDays(32))
            .ticketPrice(java.math.BigDecimal.ZERO)
            .ticketTotalQuantity(100)
            .ticketSoldQuantity(0)
            .ticketPurchaseLimit(5)
            .status(EventStatus.PREPARING)
            .boothRecruitmentEnabled(true)
            .venueMapEnabled(true)
            .boothReservationEnabled(true)
            .noShowGraceMinutes(10)
            .createdAt(now)
            .updatedAt(now)
            .build();
        eventRepository.save(event);
        eventId = event.getId();
    }

    @AfterEach
    void tearDown() {
        boothRecruitmentRepository.findByEventId(eventId).ifPresent(boothRecruitmentRepository::delete);
        eventRepository.deleteById(eventId);
        organizationRepository.deleteById(organizationId);
    }

    @Test
    void 시작시각이_지난_BEFORE_OPEN_공고는_OPEN으로_전환된다() {
        OffsetDateTime now = OffsetDateTime.now();
        BoothRecruitment recruitment = createRecruitment(now.minusMinutes(1), now.plusDays(10));
        boothRecruitmentRepository.saveAndFlush(recruitment);

        scheduler.transitionStatuses();
        entityManager.clear();

        BoothRecruitment reloaded = boothRecruitmentRepository.findById(recruitment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BoothRecruitmentStatus.OPEN);
    }

    @Test
    void 시작시각이_아직_안된_BEFORE_OPEN_공고는_전환되지_않는다() {
        OffsetDateTime now = OffsetDateTime.now();
        BoothRecruitment recruitment = createRecruitment(now.plusDays(1), now.plusDays(10));
        boothRecruitmentRepository.saveAndFlush(recruitment);

        scheduler.transitionStatuses();
        entityManager.clear();

        BoothRecruitment reloaded = boothRecruitmentRepository.findById(recruitment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BoothRecruitmentStatus.BEFORE_OPEN);
    }

    @Test
    void 종료시각이_지난_OPEN_공고는_CLOSED로_전환된다() {
        OffsetDateTime now = OffsetDateTime.now();
        BoothRecruitment recruitment = createRecruitment(now.minusDays(2), now.minusMinutes(1));
        recruitment.changeStatus(BoothRecruitmentStatus.OPEN, now.minusDays(1));
        boothRecruitmentRepository.saveAndFlush(recruitment);

        scheduler.transitionStatuses();
        entityManager.clear();

        BoothRecruitment reloaded = boothRecruitmentRepository.findById(recruitment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BoothRecruitmentStatus.CLOSED);
    }

    @Test
    void 이미_CLOSED인_공고는_스케줄러가_건드리지_않는다() {
        OffsetDateTime now = OffsetDateTime.now();
        BoothRecruitment recruitment = createRecruitment(now.minusDays(2), now.minusMinutes(1));
        recruitment.changeStatus(BoothRecruitmentStatus.CLOSED, now.minusDays(1));
        boothRecruitmentRepository.saveAndFlush(recruitment);

        scheduler.transitionStatuses();
        entityManager.clear();

        BoothRecruitment reloaded = boothRecruitmentRepository.findById(recruitment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BoothRecruitmentStatus.CLOSED);
    }

    private BoothRecruitment createRecruitment(OffsetDateTime startAt, OffsetDateTime endAt) {
        return BoothRecruitment.create(
            eventId,
            "테스트 모집 공고",
            startAt,
            endAt,
            "IT 기업",
            null,
            null,
            null,
            "홍길동",
            "contact@example.com",
            "010-1234-5678",
            null,
            OffsetDateTime.now()
        );
    }
}
