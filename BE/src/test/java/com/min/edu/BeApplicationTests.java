package com.min.edu;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "order-access-token.secret=test-order-access-token-secret-32bytes")
@AutoConfigureMockMvc
class BeApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void unmappedV1TicketOrderCreatePath_returnsNotFound() throws Exception {
        mockMvc.perform(post("/v" + "1/events/1/ticket-orders"))
            .andExpect(status().isNotFound());
    }

    @Test
    void unmappedV1MyTicketOrdersPath_returnsNotFound() throws Exception {
        mockMvc.perform(get("/v" + "1/members/me/ticket-orders"))
            .andExpect(status().isNotFound());
    }

    @Test
    void unmappedV1TicketOrderDetailPath_returnsNotFound() throws Exception {
        mockMvc.perform(get("/v" + "1/ticket-orders/ORDER-1"))
            .andExpect(status().isNotFound());
    }
}
