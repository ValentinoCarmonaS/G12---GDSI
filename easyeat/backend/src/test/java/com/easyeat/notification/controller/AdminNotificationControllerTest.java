package com.easyeat.notification.controller;

import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.controller.DishControllerTestBase;
import com.easyeat.notification.dto.NotificationPageResponse;
import com.easyeat.notification.dto.NotificationResponse;
import com.easyeat.notification.service.AdminNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminNotificationController.class)
@DisplayName("AdminNotificationControllerTest — Web Contract Tests for Admin Notification Endpoints (T060, CHF-2.2.4)")
class AdminNotificationControllerTest extends DishControllerTestBase {

    @MockBean
    private AdminNotificationService adminNotificationService;

    private final UUID notificationId = UUID.randomUUID();
    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;
    private final UUID chefId = DishTestFixtures.DEFAULT_CHEF_ID;

    @Nested
    @DisplayName("GET /api/v1/admin/notifications - List Admin Notifications Contract Tests")
    class ListNotificationsContractTests {

        @Test
        @WithMockUser(roles = "ADMINISTRADOR")
        @DisplayName("Should return 200 OK and NotificationPageResponse when authenticated as ADMINISTRADOR")
        void listNotifications_shouldReturn200_whenAdmin() throws Exception {
            NotificationResponse item = new NotificationResponse(
                    notificationId,
                    defaultTenantId,
                    "DISH_SUBMITTED_FOR_REVIEW",
                    "Nuevo plato enviado a revisión",
                    "El Chef ha enviado la ficha técnica 'Risotto de Hongos' para revisión de precios.",
                    dishId,
                    chefId,
                    false,
                    Instant.now()
            );
            NotificationPageResponse pageResponse = new NotificationPageResponse(
                    List.of(item),
                    1,
                    0,
                    20,
                    1,
                    1
            );

            when(adminNotificationService.listNotifications(eq(false), any(Pageable.class))).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/admin/notifications")
                            .param("unreadOnly", "false")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(notificationId.toString()))
                    .andExpect(jsonPath("$.content[0].type").value("DISH_SUBMITTED_FOR_REVIEW"))
                    .andExpect(jsonPath("$.content[0].isRead").value(false))
                    .andExpect(jsonPath("$.unreadCount").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when request has no token")
        void listNotifications_shouldReturn401_whenUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/admin/notifications"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 403 Forbidden when user is CHEF instead of ADMINISTRADOR")
        void listNotifications_shouldReturn403_whenRoleChef() throws Exception {
            mockMvc.perform(get("/api/v1/admin/notifications"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/admin/notifications/{id}/read - Mark Notification as Read Contract Tests")
    class MarkAsReadContractTests {

        @Test
        @WithMockUser(roles = "ADMINISTRADOR")
        @DisplayName("Should return 200 OK and NotificationResponse when marked as read")
        void markAsRead_shouldReturn200_whenValid() throws Exception {
            NotificationResponse response = new NotificationResponse(
                    notificationId,
                    defaultTenantId,
                    "DISH_SUBMITTED_FOR_REVIEW",
                    "Nuevo plato enviado a revisión",
                    "Mensaje",
                    dishId,
                    chefId,
                    true,
                    Instant.now()
            );

            when(adminNotificationService.markAsRead(notificationId)).thenReturn(response);

            mockMvc.perform(patch("/api/v1/admin/notifications/{id}/read", notificationId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(notificationId.toString()))
                    .andExpect(jsonPath("$.isRead").value(true));
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when request has no token")
        void markAsRead_shouldReturn401_whenUnauthenticated() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/notifications/{id}/read", notificationId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 403 Forbidden when user is CHEF instead of ADMINISTRADOR")
        void markAsRead_shouldReturn403_whenRoleChef() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/notifications/{id}/read", notificationId))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMINISTRADOR")
        @DisplayName("Should return 404 Not Found when notification does not exist")
        void markAsRead_shouldReturn404_whenNotFound() throws Exception {
            when(adminNotificationService.markAsRead(notificationId)).thenThrow(new NoSuchElementException("Notificación no encontrada"));

            mockMvc.perform(patch("/api/v1/admin/notifications/{id}/read", notificationId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Resource Not Found"));
        }
    }
}
