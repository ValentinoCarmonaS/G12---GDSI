package com.easyeat.notification.service;

import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.notification.domain.AdminNotification;
import com.easyeat.notification.domain.NotificationType;
import com.easyeat.notification.dto.NotificationPageResponse;
import com.easyeat.notification.dto.NotificationResponse;
import com.easyeat.notification.repository.AdminNotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminNotificationServiceTest — Notification Service Unit Tests (T058, CHF-2.2.4)")
class AdminNotificationServiceTest {

    @Mock
    private AdminNotificationRepository adminNotificationRepository;

    @InjectMocks
    private AdminNotificationService adminNotificationService;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID notificationId = UUID.randomUUID();
    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;
    private final UUID chefId = UUID.randomUUID();

    private AdminNotification notification;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);

        notification = new AdminNotification(
                tenantId,
                NotificationType.DISH_SUBMITTED_FOR_REVIEW,
                "Nuevo plato enviado a revisión",
                "El Chef ha enviado la ficha técnica 'Risotto' para revisión de precios.",
                dishId,
                chefId
        );
        notification.setId(notificationId);
        notification.setCreatedAt(Instant.now());
        notification.setIsRead(false);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("Notification Creation Tests")
    class CreationTests {

        @Test
        @DisplayName("Should create and persist AdminNotification with isRead = false")
        void createNotification_shouldPersistAndReturnNotification() {
            when(adminNotificationRepository.save(any(AdminNotification.class))).thenAnswer(inv -> inv.getArgument(0));

            AdminNotification created = adminNotificationService.createNotification(
                    tenantId,
                    NotificationType.DISH_SUBMITTED_FOR_REVIEW,
                    "Nuevo plato",
                    "Mensaje descriptivo",
                    dishId,
                    chefId
            );

            assertThat(created).isNotNull();
            assertThat(created.getTenantId()).isEqualTo(tenantId);
            assertThat(created.getType()).isEqualTo(NotificationType.DISH_SUBMITTED_FOR_REVIEW);
            assertThat(created.getTitle()).isEqualTo("Nuevo plato");
            assertThat(created.getMessage()).isEqualTo("Mensaje descriptivo");
            assertThat(created.getDishId()).isEqualTo(dishId);
            assertThat(created.getChefId()).isEqualTo(chefId);
            assertThat(created.isRead()).isFalse();

            verify(adminNotificationRepository).save(any(AdminNotification.class));
        }
    }

    @Nested
    @DisplayName("Notification Listing & Filtering Tests")
    class ListingTests {

        @Test
        @DisplayName("Should list all notifications paginated when unreadOnly is false")
        void listNotifications_shouldReturnAllPaginated_whenUnreadOnlyFalse() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<AdminNotification> page = new PageImpl<>(List.of(notification), pageable, 1);

            when(adminNotificationRepository.findByTenantId(tenantId, pageable)).thenReturn(page);
            when(adminNotificationRepository.countByTenantIdAndIsReadFalse(tenantId)).thenReturn(1L);

            NotificationPageResponse response = adminNotificationService.listNotifications(false, pageable);

            assertThat(response).isNotNull();
            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).id()).isEqualTo(notificationId);
            assertThat(response.unreadCount()).isEqualTo(1L);
            assertThat(response.totalElements()).isEqualTo(1L);

            verify(adminNotificationRepository).findByTenantId(tenantId, pageable);
        }

        @Test
        @DisplayName("Should list only unread notifications paginated when unreadOnly is true")
        void listNotifications_shouldReturnUnreadPaginated_whenUnreadOnlyTrue() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<AdminNotification> page = new PageImpl<>(List.of(notification), pageable, 1);

            when(adminNotificationRepository.findByTenantIdAndIsRead(tenantId, false, pageable)).thenReturn(page);
            when(adminNotificationRepository.countByTenantIdAndIsReadFalse(tenantId)).thenReturn(1L);

            NotificationPageResponse response = adminNotificationService.listNotifications(true, pageable);

            assertThat(response).isNotNull();
            assertThat(response.content()).hasSize(1);
            assertThat(response.unreadCount()).isEqualTo(1L);

            verify(adminNotificationRepository).findByTenantIdAndIsRead(tenantId, false, pageable);
        }

        @Test
        @DisplayName("Should return unread count for current tenant")
        void getUnreadCount_shouldReturnCountFromRepository() {
            when(adminNotificationRepository.countByTenantIdAndIsReadFalse(tenantId)).thenReturn(5L);

            long count = adminNotificationService.getUnreadCount();

            assertThat(count).isEqualTo(5L);
            verify(adminNotificationRepository).countByTenantIdAndIsReadFalse(tenantId);
        }
    }

    @Nested
    @DisplayName("Mark as Read Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark notification as read and return updated response")
        void markAsRead_shouldUpdateStatusAndReturnResponse() {
            when(adminNotificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
            when(adminNotificationRepository.save(any(AdminNotification.class))).thenAnswer(inv -> inv.getArgument(0));

            NotificationResponse response = adminNotificationService.markAsRead(notificationId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(notificationId);
            assertThat(response.isRead()).isTrue();

            verify(adminNotificationRepository).save(notification);
            assertThat(notification.isRead()).isTrue();
        }

        @Test
        @DisplayName("Should throw NoSuchElementException when notification does not exist")
        void markAsRead_shouldThrowNotFound_whenNotificationDoesNotExist() {
            when(adminNotificationRepository.findById(notificationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminNotificationService.markAsRead(notificationId))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("Should throw NoSuchElementException when notification belongs to different tenant")
        void markAsRead_shouldThrowNotFound_whenDifferentTenant() {
            notification.setTenantId(UUID.randomUUID());
            when(adminNotificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

            assertThatThrownBy(() -> adminNotificationService.markAsRead(notificationId))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }
}
