package com.tradex.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.auth.dto.RegisterRequest;
import com.tradex.notification.enums.NotificationType;
import com.tradex.notification.service.NotificationService;
import com.tradex.user.entity.User;
import com.tradex.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    private String user1Token;
    private String user2Token;
    private User user1;
    private User user2;

    @BeforeEach
    void setUp() throws Exception {
        RegisterRequest req1 = new RegisterRequest("notifuser1@tradex.com", "password123", "Notif User One");
        MvcResult res1 = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated()).andReturn();
        user1Token = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data").path("accessToken").asText();
        user1 = userRepository.findByEmail("notifuser1@tradex.com").orElseThrow();

        RegisterRequest req2 = new RegisterRequest("notifuser2@tradex.com", "password123", "Notif User Two");
        MvcResult res2 = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isCreated()).andReturn();
        user2Token = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data").path("accessToken").asText();
        user2 = userRepository.findByEmail("notifuser2@tradex.com").orElseThrow();
    }

    @Test
    @DisplayName("GET & PATCH /api/notifications — Authenticated user access and ownership isolation")
    void notificationApiAndSecurityIsolation() throws Exception {
        UUID eventId1 = UUID.randomUUID();
        var notif1 = notificationService.createNotificationIdempotent(user1.getId(), NotificationType.ORDER_CREATED, "Order 1", "Message 1", eventId1).orElseThrow();

        // 1. User 1 retrieves their notifications
        mockMvc.perform(get("/api/notifications?page=0&size=10")
                .header("Authorization", "Bearer " + user1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].title").value("Order 1"));

        // 2. User 2 cannot see User 1's notifications
        mockMvc.perform(get("/api/notifications?page=0&size=10")
                .header("Authorization", "Bearer " + user2Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty());

        // 3. User 2 attempts to mark User 1's notification as read -> Rejects with 422 ACCESS_DENIED
        mockMvc.perform(patch("/api/notifications/" + notif1.getId() + "/read")
                .header("Authorization", "Bearer " + user2Token))
            .andExpect(status().isUnprocessableEntity());

        // 4. User 1 marks their own notification as read -> Succeeds
        mockMvc.perform(patch("/api/notifications/" + notif1.getId() + "/read")
                .header("Authorization", "Bearer " + user1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("READ"));
    }
}
