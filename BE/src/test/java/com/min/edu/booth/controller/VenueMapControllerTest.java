package com.min.edu.booth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.min.edu.booth.domain.Booth;
import com.min.edu.common.security.jwt.JwtTokenProvider;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventMember;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.file.domain.FileAccessLevel;
import com.min.edu.file.domain.FileAsset;
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
class VenueMapControllerTest {

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
    private JwtTokenProvider jwtTokenProvider;

    private Long eventId;
    private Long boothId;
    private Long imageFileId;
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
            .ticketPrice(BigDecimal.ZERO)
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

        Booth booth = Booth.create(
            eventId, "A-01", "표준부스", "1층", "A구역", null,
            null, null, null, null,
            false, false, false, false,
            BigDecimal.valueOf(500000), now
        );
        entityManager.persist(booth);
        boothId = booth.getId();

        FileAsset fileAsset = FileAsset.builder()
            .uploadedBy(eventManager.getId())
            .storageKey("venue-maps/test.png")
            .originalName("floorplan.png")
            .mimeType("image/png")
            .fileSize(1024L)
            .accessLevel(FileAccessLevel.PUBLIC)
            .createdAt(now)
            .build();
        entityManager.persist(fileAsset);
        imageFileId = fileAsset.getId();

        entityManager.flush();

        eventManagerToken = jwtTokenProvider.createAccessToken(eventManager.getId(), PlatformRole.USER);
        outsiderToken = jwtTokenProvider.createAccessToken(outsider.getId(), PlatformRole.USER);
    }

    private String createMapJson() throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("mapType", "VISITOR");
            put("floorName", "1층");
            put("imageFileId", imageFileId);
            put("originalWidth", 1200);
            put("originalHeight", 800);
        }});
    }

    @Test
    void 담당자는_평면도를_등록할_수_있다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/venue-maps", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMapJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.floorName").value("1층"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void 담당자가_아니면_평면도를_등록할_수_없다() throws Exception {
        mockMvc.perform(post("/events/{eventId}/venue-maps", eventId)
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMapJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_403"));
    }

    @Test
    void 평면도를_게시하면_공개_조회에_노출된다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/venue-maps", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMapJson()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long mapId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/events/{eventId}/venue-maps/public", eventId)
                .param("mapType", "VISITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(patch("/events/{eventId}/venue-maps/{mapId}/publish", eventId, mapId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/events/{eventId}/venue-maps/public", eventId)
                .param("mapType", "VISITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].floorName").value("1층"));
    }

    @Test
    void 같은_mapType이라도_층이_다르면_동시에_게시될_수_있다() throws Exception {
        String firstFloorJson = createMapJson();
        String secondFloorJson = objectMapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("mapType", "VISITOR");
            put("floorName", "2층");
            put("imageFileId", imageFileId);
            put("originalWidth", 1200);
            put("originalHeight", 800);
        }});

        long firstMapId = createAndPublish(firstFloorJson);
        long secondMapId = createAndPublish(secondFloorJson);

        mockMvc.perform(get("/events/{eventId}/venue-maps/public", eventId)
                .param("mapType", "VISITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].floorName").value("1층"))
            .andExpect(jsonPath("$.data[1].floorName").value("2층"));

        assertThat(firstMapId).isNotEqualTo(secondMapId);
    }

    private long createAndPublish(String createJson) throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/venue-maps", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long mapId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(patch("/events/{eventId}/venue-maps/{mapId}/publish", eventId, mapId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk());

        return mapId;
    }

    @Test
    void 부스_좌표를_저장하고_조회할_수_있다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/venue-maps", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMapJson()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long mapId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        String positionsJson = objectMapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("positions", List.of(new LinkedHashMap<String, Object>() {{
                put("boothId", boothId);
                put("xRatio", 0.5);
                put("yRatio", 0.25);
            }}));
        }});

        mockMvc.perform(put("/events/{eventId}/venue-maps/{mapId}/positions", eventId, mapId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(positionsJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.positions.length()").value(1))
            .andExpect(jsonPath("$.data.positions[0].boothCode").value("A-01"))
            .andExpect(jsonPath("$.data.positions[0].xRatio").value(0.5));
    }

    @Test
    void DRAFT_상태의_평면도만_삭제할_수_있다() throws Exception {
        String createResponse = mockMvc.perform(post("/events/{eventId}/venue-maps", eventId)
                .header("Authorization", "Bearer " + eventManagerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMapJson()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long mapId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(patch("/events/{eventId}/venue-maps/{mapId}/publish", eventId, mapId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/events/{eventId}/venue-maps/{mapId}", eventId, mapId)
                .header("Authorization", "Bearer " + eventManagerToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_400"));
    }
}
