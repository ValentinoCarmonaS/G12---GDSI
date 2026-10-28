package com.easyeat.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AdminNotificationTest {

    @Test
    @DisplayName("NotificationType should have DISH_SUBMITTED_FOR_REVIEW")
    void notificationTypeValues() {
        assertThat(NotificationType.values()).containsExactly(
                NotificationType.DISH_SUBMITTED_FOR_REVIEW
        );
        assertThat(NotificationType.valueOf("DISH_SUBMITTED_FOR_REVIEW"))
                .isEqualTo(NotificationType.DISH_SUBMITTED_FOR_REVIEW);
    }

    @Test
    @DisplayName("Default constructor initializes default values")
    void defaultConstructor() {
        AdminNotification notification = new AdminNotification();

        assertThat(notification.getId()).isNull();
        assertThat(notification.getTenantId()).isNull();
        assertThat(notification.getType()).isNull();
        assertThat(notification.getTitle()).isNull();
        assertThat(notification.getMessage()).isNull();
        assertThat(notification.getDishId()).isNull();
        assertThat(notification.getChefId()).isNull();
        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(notification.getCreatedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Parameterized constructor initializes all provided fields and defaults")
    void parameterizedConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID dishId = UUID.randomUUID();
        UUID chefId = UUID.randomUUID();
        NotificationType type = NotificationType.DISH_SUBMITTED_FOR_REVIEW;
        String title = "Nuevo plato enviado a revisión";
        String message = "El plato 'Risotto de hongos' fue enviado para revisión por Chef.";

        AdminNotification notification = new AdminNotification(tenantId, type, title, message, dishId, chefId);

        assertThat(notification.getId()).isNull();
        assertThat(notification.getTenantId()).isEqualTo(tenantId);
        assertThat(notification.getType()).isEqualTo(type);
        assertThat(notification.getTitle()).isEqualTo(title);
        assertThat(notification.getMessage()).isEqualTo(message);
        assertThat(notification.getDishId()).isEqualTo(dishId);
        assertThat(notification.getChefId()).isEqualTo(chefId);
        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(notification.getCreatedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Getters and setters work correctly for all fields")
    void gettersAndSetters() {
        AdminNotification notification = new AdminNotification();

        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID dishId = UUID.randomUUID();
        UUID chefId = UUID.randomUUID();
        Instant now = Instant.now().minus(1, ChronoUnit.HOURS);

        notification.setId(id);
        notification.setTenantId(tenantId);
        notification.setType(NotificationType.DISH_SUBMITTED_FOR_REVIEW);
        notification.setTitle("Actualización de plato");
        notification.setMessage("Mensaje detallado");
        notification.setDishId(dishId);
        notification.setChefId(chefId);
        notification.setIsRead(true);
        notification.setCreatedAt(now);

        assertThat(notification.getId()).isEqualTo(id);
        assertThat(notification.getTenantId()).isEqualTo(tenantId);
        assertThat(notification.getType()).isEqualTo(NotificationType.DISH_SUBMITTED_FOR_REVIEW);
        assertThat(notification.getTitle()).isEqualTo("Actualización de plato");
        assertThat(notification.getMessage()).isEqualTo("Mensaje detallado");
        assertThat(notification.getDishId()).isEqualTo(dishId);
        assertThat(notification.getChefId()).isEqualTo(chefId);
        assertThat(notification.getIsRead()).isTrue();
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("markAsRead sets isRead to true")
    void markAsRead() {
        AdminNotification notification = new AdminNotification();
        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.isRead()).isFalse();

        notification.markAsRead();

        assertThat(notification.getIsRead()).isTrue();
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("isRead handles null safely")
    void isReadHandlesNull() {
        AdminNotification notification = new AdminNotification();
        notification.setIsRead(null);

        assertThat(notification.getIsRead()).isNull();
        assertThat(notification.isRead()).isFalse();
    }
}
