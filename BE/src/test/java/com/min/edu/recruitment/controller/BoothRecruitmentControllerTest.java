package com.min.edu.recruitment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.min.edu.TestcontainersConfiguration;
import com.min.edu.common.security.jwt.JwtTokenProvider;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventMember;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.OauthProvider;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.member.repository.MemberRepository;
import com.min.edu.organization.domain.Organization;
import com.min.edu.organization.domain.OrganizationStatus;
import com.min.edu.organization.domain.OrganizationType;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class BoothRecruitmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventMemberRepository eventMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Long eventId;
    private String eventManagerToken;
    private String outsiderToken;

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
        entityManager.persist(organization);

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

        Member eventManager = Member.createOAuthMember(
            "manager@example.com", "매니저", OauthProvider.GOOGLE, "manager-sub", now
        );
        memberRepository.save(eventManager);

        Member outsider = Member.createOAuthMember(
            "outsider@example.com", "외부인", OauthProvider.GOOGLE, "outsider-sub", now
        );
        memberRepository.save(outsider);

        EventMember eventMember = EventMember.builder()
            .eventId(eventId)
            .memberId(eventManager.getId())
            .eventRole(EventRole.EVENT_MANAGER)
            .active(true)
            .createdAt(now)
            .build();
        entityManager.persist(eventMember);

        entityManager.flush();

        eventManagerToken = jwtTokenProvider.createAccessToken(eventManager.getId(), PlatformRole.USER);
        outsiderToken = jwtTokenProvider.createAccessToken(outsider.getId(), PlatformRole.USER);
    }

    @Test
    void 담당자는_모집공고를_생성할_수_있다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.eventId").value(eventId))
            .andExpect(jsonPath("$.data.status").value("BEFORE_OPEN"));
    }

    @Test
    void 담당자가_아니면_생성할_수_없다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_403"));
    }

    @Test
    void 같은_행사에_중복_생성하면_실패한다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_400_002"));
    }

    @Test
    void BEFORE_OPEN_상태는_공개_목록에_보이지_않는다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/booth-recruitments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/events/{eventId}/booth-recruitment/management", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("BEFORE_OPEN"));
    }

    @Test
    void 조기마감_후_완료처리_흐름() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/events/{eventId}/booth-recruitment/completion", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_400_003"));

        mockMvc.perform(post("/events/{eventId}/booth-recruitment/closure", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(post("/events/{eventId}/booth-recruitment/completion", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    private String createRequestJson() throws Exception {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        OffsetDateTime end = OffsetDateTime.now().plusDays(10);

        return objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
            put("title", "테스트 모집 공고");
            put("recruitmentStartAt", start.toString());
            put("recruitmentEndAt", end.toString());
            put("participantTarget", "IT 기업");
            put("contactName", "홍길동");
            put("contactEmail", "contact@example.com");
            put("contactPhone", "010-1234-5678");
        }});
    }
}
