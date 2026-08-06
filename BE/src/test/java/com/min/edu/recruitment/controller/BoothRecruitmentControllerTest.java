package com.min.edu.recruitment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
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
import com.min.edu.recruitment.repository.BoothRecruitmentRepository;

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

    @Autowired
    private BoothRecruitmentRepository boothRecruitmentRepository;

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

    @Test
    void BEFORE_OPEN_상태의_상세조회는_404이고_공개된_후에는_조회된다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long recruitmentId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/booth-recruitments/{recruitmentId}", recruitmentId))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/events/{eventId}/booth-recruitment/closure", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/booth-recruitments/{recruitmentId}", recruitmentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(recruitmentId))
            .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void 담당자는_모집공고를_수정할_수_있다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        OffsetDateTime newStart = OffsetDateTime.now().plusDays(2);
        OffsetDateTime newEnd = OffsetDateTime.now().plusDays(20);
        String updateJson = objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
            put("title", "수정된 모집 공고");
            put("recruitmentStartAt", newStart.toString());
            put("recruitmentEndAt", newEnd.toString());
            put("participantTarget", "제조업 기업");
            put("contactName", "김수정");
            put("contactEmail", "updated@example.com");
            put("contactPhone", "010-9999-8888");
            put("businessNumberRequired", false);
        }});

        mockMvc.perform(patch("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("수정된 모집 공고"))
            .andExpect(jsonPath("$.data.contactName").value("김수정"));

        mockMvc.perform(get("/events/{eventId}/booth-recruitment/management", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("수정된 모집 공고"));
    }

    @Test
    void COMPLETED_상태의_모집공고는_수정할_수_없다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/events/{eventId}/booth-recruitment/closure", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/events/{eventId}/booth-recruitment/completion", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_400_003"));
    }

    @Test
    void 담당자가_아니면_수정할_수_없다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_403"));
    }

    @Test
    void OPEN_상태에서는_전체_수정이_불가능하다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        openRecruitment();

        mockMvc.perform(patch("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_400_003"));
    }

    @Test
    void OPEN_상태에서는_모집_종료일만_수정할_수_있다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        openRecruitment();

        OffsetDateTime newEnd = OffsetDateTime.now().plusDays(30);
        String endAtJson = objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
            put("recruitmentEndAt", newEnd.toString());
        }});

        mockMvc.perform(patch("/events/{eventId}/booth-recruitment/end-at", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(endAtJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void BEFORE_OPEN_상태에서는_종료일만_수정하는_API를_쓸_수_없다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        OffsetDateTime newEnd = OffsetDateTime.now().plusDays(30);
        String endAtJson = objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
            put("recruitmentEndAt", newEnd.toString());
        }});

        mockMvc.perform(patch("/events/{eventId}/booth-recruitment/end-at", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(endAtJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_400_003"));
    }

    @Test
    void BEFORE_OPEN_상태의_모집공고는_삭제할_수_있다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/events/{eventId}/booth-recruitment/management", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void OPEN_이후_상태의_모집공고는_삭제할_수_없다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/events/{eventId}/booth-recruitment/closure", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_400_003"));
    }

    @Test
    void 담당자가_아니면_삭제할_수_없다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson()))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/events/{eventId}/booth-recruitment", eventId)
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_403"));
    }

    private void openRecruitment() {
        BoothRecruitment recruitment = boothRecruitmentRepository.findByEventId(eventId).orElseThrow();
        recruitment.changeStatus(BoothRecruitmentStatus.OPEN, OffsetDateTime.now());
        boothRecruitmentRepository.saveAndFlush(recruitment);
        entityManager.clear();
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
            put("businessNumberRequired", true);
        }});
    }
}
