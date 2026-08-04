package com.min.edu.booth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;

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
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationStatus;
import com.min.edu.organization.domain.OrganizationType;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class BoothControllerTest {

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
    private String exhibitorToken;
    private Long exhibitorOrganizationId;

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

        Member exhibitor = Member.createOAuthMember(
            "exhibitor@example.com", "참가기업", OauthProvider.GOOGLE, "exhibitor-sub", now
        );
        memberRepository.save(exhibitor);

        EventMember eventMember = EventMember.builder()
            .eventId(eventId)
            .memberId(eventManager.getId())
            .eventRole(EventRole.EVENT_MANAGER)
            .active(true)
            .createdAt(now)
            .build();
        entityManager.persist(eventMember);

        Organization exhibitorOrganization = Organization.builder()
            .organizationType(OrganizationType.EXHIBITOR)
            .name("테스트참가기업")
            .contactEmail("exhibitor-org@example.com")
            .contactPhone("010-1111-2222")
            .status(OrganizationStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();
        entityManager.persist(exhibitorOrganization);
        exhibitorOrganizationId = exhibitorOrganization.getId();

        OrganizationMember organizationMember = OrganizationMember.createOwner(
            exhibitorOrganizationId, exhibitor.getId(), now
        );
        entityManager.persist(organizationMember);

        entityManager.flush();

        eventManagerToken = jwtTokenProvider.createAccessToken(eventManager.getId(), PlatformRole.USER);
        outsiderToken = jwtTokenProvider.createAccessToken(outsider.getId(), PlatformRole.USER);
        exhibitorToken = jwtTokenProvider.createAccessToken(exhibitor.getId(), PlatformRole.USER);
    }

    private void assignToExhibitor(Long boothId) {
        entityManager.createQuery(
                "UPDATE Booth b SET b.assignedOrganizationId = :orgId WHERE b.id = :boothId")
            .setParameter("orgId", exhibitorOrganizationId)
            .setParameter("boothId", boothId)
            .executeUpdate();
        entityManager.clear();
    }

    @Test
    void 담당자는_부스를_등록할_수_있다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("A-01")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.boothCode").value("A-01"))
            .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    void 같은_행사에_부스번호가_중복되면_등록할_수_없다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("A-01")))
            .andExpect(status().isOk());

        mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("A-01")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BOOTH_400_001"));
    }

    @Test
    void 담당자가_아니면_부스를_등록할_수_없다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("A-01")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_403"));
    }

    @Test
    void 부스를_일괄_등록할_수_있다() throws Exception {
        String bulkJson = objectMapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("boothCodes", List.of("B-01", "B-02", "B-03"));
            put("boothType", "표준부스");
            put("price", 500000);
        }});

        mockMvc.perform(post("/events/{eventId}/booths/bulk", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bulkJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].boothCode").value("B-01"))
            .andExpect(jsonPath("$.data[2].boothCode").value("B-03"));
    }

    @Test
    void 일괄_등록시_부스번호가_중복되면_실패한다() throws Exception {
        String bulkJson = objectMapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("boothCodes", List.of("C-01", "C-01"));
            put("boothType", "표준부스");
            put("price", 500000);
        }});

        mockMvc.perform(post("/events/{eventId}/booths/bulk", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bulkJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BOOTH_400_001"));
    }

    @Test
    void 부스_목록을_상태로_필터링할_수_있다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("D-01")))
            .andExpect(status().isOk());

        mockMvc.perform(get("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .param("status", "AVAILABLE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].boothCode").value("D-01"));

        mockMvc.perform(get("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .param("status", "ASSIGNED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void 부스_상세를_조회할_수_있다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("E-01")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/events/{eventId}/booths/{boothId}", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.boothCode").value("E-01"));
    }

    @Test
    void 존재하지_않는_부스_상세조회는_404이다() throws Exception {
        mockMvc.perform(get("/events/{eventId}/booths/{boothId}", eventId, 999_999L)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void 담당자는_부스_정보를_수정할_수_있다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("F-01")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        String updateJson = objectMapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("boothCode", "F-01");
            put("boothType", "프리미엄부스");
            put("price", 900000);
        }});

        mockMvc.perform(patch("/events/{eventId}/booths/{boothId}", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.boothType").value("프리미엄부스"))
            .andExpect(jsonPath("$.data.price").value(900000));
    }

    @Test
    void 담당자는_부스_상태를_변경할_수_있다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("G-01")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(patch("/events/{eventId}/booths/{boothId}/status", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNAVAILABLE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"));
    }

    @Test
    void AVAILABLE_상태의_부스는_삭제할_수_있다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("H-01")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(delete("/events/{eventId}/booths/{boothId}", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/events/{eventId}/booths/{boothId}", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void AVAILABLE이_아닌_부스는_삭제할_수_없다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("I-01")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(patch("/events/{eventId}/booths/{boothId}/status", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ASSIGNED\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/events/{eventId}/booths/{boothId}", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BOOTH_400_002"));
    }

    @Test
    void 담당자가_아니면_수정_상태변경_삭제를_할_수_없다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("J-01")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(patch("/events/{eventId}/booths/{boothId}", eventId, boothId)
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("J-01")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_403"));

        mockMvc.perform(delete("/events/{eventId}/booths/{boothId}", eventId, boothId)
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_403"));
    }

    @Test
    void 배정된_조직_구성원은_ASSIGNED_부스의_소개를_수정할_수_있다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("K-01")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(patch("/events/{eventId}/booths/{boothId}/status", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ASSIGNED\"}"))
            .andExpect(status().isOk());

        assignToExhibitor(boothId);

        String introJson = objectMapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("displayName", "테스트 부스");
            put("shortIntro", "한 줄 소개");
            put("description", "상세 소개");
        }});

        mockMvc.perform(patch("/events/{eventId}/booths/{boothId}/intro", eventId, boothId)
                .header("Authorization", "Bearer " + exhibitorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(introJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.displayName").value("테스트 부스"))
            .andExpect(jsonPath("$.data.shortIntro").value("한 줄 소개"));
    }

    @Test
    void ASSIGNED_상태가_아니면_소개를_수정할_수_없다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("K-02")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        String introJson = objectMapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("displayName", "테스트 부스");
        }});

        mockMvc.perform(patch("/events/{eventId}/booths/{boothId}/intro", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(introJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BOOTH_400_003"));
    }

    @Test
    void 배정되지_않은_조직의_구성원은_소개를_수정할_수_없다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("K-03")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(patch("/events/{eventId}/booths/{boothId}/status", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ASSIGNED\"}"))
            .andExpect(status().isOk());

        String introJson = objectMapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("displayName", "테스트 부스");
        }});

        mockMvc.perform(patch("/events/{eventId}/booths/{boothId}/intro", eventId, boothId)
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(introJson))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_403"));
    }

    @Test
    void 담당자는_QR을_발급하고_조회할_수_있다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("L-01")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        String issueResponse = mockMvc.perform(post("/events/{eventId}/booths/{boothId}/qr", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.qrToken").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String firstToken = objectMapper.readTree(issueResponse).path("data").path("qrToken").asText();

        mockMvc.perform(get("/events/{eventId}/booths/{boothId}/qr", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.qrToken").value(firstToken));

        // 재발급하면 토큰이 갱신된다.
        mockMvc.perform(post("/events/{eventId}/booths/{boothId}/qr", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.qrToken").value(org.hamcrest.Matchers.not(firstToken)));
    }

    @Test
    void 담당자가_아니면_QR을_발급할_수_없다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("L-02")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(post("/events/{eventId}/booths/{boothId}/qr", eventId, boothId)
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_403"));
    }

    @Test
    void 배정된_조직_구성원도_QR을_조회할_수_있다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("L-03")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long boothId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(patch("/events/{eventId}/booths/{boothId}/status", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ASSIGNED\"}"))
            .andExpect(status().isOk());

        assignToExhibitor(boothId);

        mockMvc.perform(post("/events/{eventId}/booths/{boothId}/qr", eventId, boothId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/events/{eventId}/booths/{boothId}/qr", eventId, boothId)
                .header("Authorization", "Bearer " + exhibitorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.qrToken").isNotEmpty());
    }

    @Test
    void 키워드로_부스를_검색할_수_있다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("M-01")))
            .andExpect(status().isOk());

        mockMvc.perform(post("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson("N-01")))
            .andExpect(status().isOk());

        mockMvc.perform(get("/events/{eventId}/booths", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .param("keyword", "m-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].boothCode").value("M-01"));
    }

    private String createRequestJson(String boothCode) throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("boothCode", boothCode);
            put("boothType", "표준부스");
            put("floorName", "1층");
            put("zoneName", "A구역");
            put("price", 500000);
        }});
    }
}
