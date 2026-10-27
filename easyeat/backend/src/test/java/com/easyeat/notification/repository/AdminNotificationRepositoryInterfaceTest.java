package com.easyeat.notification.repository;

import com.easyeat.notification.domain.AdminNotification;
import com.easyeat.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("AdminNotificationRepository Interface & Contract Tests")
class AdminNotificationRepositoryInterfaceTest {

    @Test
    @DisplayName("Should be an interface annotated with @Repository and extend JpaRepository<AdminNotification, UUID>")
    void shouldBeProperJpaRepositoryInterface() {
        Class<?> clazz = AdminNotificationRepository.class;

        assertThat(clazz.isInterface()).isTrue();
        assertThat(clazz.isAnnotationPresent(Repository.class)).isTrue();
        assertThat(JpaRepository.class.isAssignableFrom(clazz)).isTrue();

        Type[] genericInterfaces = clazz.getGenericInterfaces();
        boolean hasExpectedJpaRepoGeneric = false;
        for (Type type : genericInterfaces) {
            if (type instanceof ParameterizedType pt && pt.getRawType().equals(JpaRepository.class)) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length == 2 && typeArgs[0].equals(AdminNotification.class) && typeArgs[1].equals(UUID.class)) {
                    hasExpectedJpaRepoGeneric = true;
                }
            }
        }
        assertThat(hasExpectedJpaRepoGeneric)
                .as("AdminNotificationRepository must extend JpaRepository<AdminNotification, UUID>")
                .isTrue();
    }

    @Test
    @DisplayName("Should declare findByTenantId returning Page<AdminNotification>")
    void shouldDeclareFindByTenantId() throws NoSuchMethodException {
        Method method = AdminNotificationRepository.class.getMethod("findByTenantId", UUID.class, Pageable.class);
        assertThat(method.getReturnType()).isEqualTo(Page.class);

        Type returnType = method.getGenericReturnType();
        assertThat(returnType).isInstanceOf(ParameterizedType.class);
        Type[] typeArgs = ((ParameterizedType) returnType).getActualTypeArguments();
        assertThat(typeArgs).containsExactly(AdminNotification.class);
    }

    @Test
    @DisplayName("Should declare findByTenantIdAndIsRead returning Page<AdminNotification>")
    void shouldDeclareFindByTenantIdAndIsRead() throws NoSuchMethodException {
        Method method = AdminNotificationRepository.class.getMethod("findByTenantIdAndIsRead", UUID.class, Boolean.class, Pageable.class);
        assertThat(method.getReturnType()).isEqualTo(Page.class);

        Type returnType = method.getGenericReturnType();
        assertThat(returnType).isInstanceOf(ParameterizedType.class);
        Type[] typeArgs = ((ParameterizedType) returnType).getActualTypeArguments();
        assertThat(typeArgs).containsExactly(AdminNotification.class);
    }

    @Test
    @DisplayName("Should declare findByTenantIdAndIsReadFalseOrderByCreatedAtDesc returning List<AdminNotification>")
    void shouldDeclareFindByTenantIdAndIsReadFalseOrderByCreatedAtDesc() throws NoSuchMethodException {
        Method method = AdminNotificationRepository.class.getMethod("findByTenantIdAndIsReadFalseOrderByCreatedAtDesc", UUID.class);
        assertThat(method.getReturnType()).isEqualTo(List.class);

        Type returnType = method.getGenericReturnType();
        assertThat(returnType).isInstanceOf(ParameterizedType.class);
        Type[] typeArgs = ((ParameterizedType) returnType).getActualTypeArguments();
        assertThat(typeArgs).containsExactly(AdminNotification.class);
    }

    @Test
    @DisplayName("Should declare countByTenantIdAndIsReadFalse returning long")
    void shouldDeclareCountByTenantIdAndIsReadFalse() throws NoSuchMethodException {
        Method method = AdminNotificationRepository.class.getMethod("countByTenantIdAndIsReadFalse", UUID.class);
        assertThat(method.getReturnType()).isEqualTo(long.class);
    }

    @Test
    @DisplayName("Should be invocable via mockito proxy")
    void shouldBeInvocableViaMock() {
        AdminNotificationRepository mockRepo = Mockito.mock(AdminNotificationRepository.class);
        UUID tenantId = UUID.randomUUID();
        UUID dishId = UUID.randomUUID();
        UUID chefId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        AdminNotification notification = new AdminNotification(
                tenantId,
                NotificationType.DISH_SUBMITTED_FOR_REVIEW,
                "Nuevo plato enviado",
                "El chef envió un nuevo plato para revisión",
                dishId,
                chefId
        );
        Page<AdminNotification> page = new PageImpl<>(List.of(notification));

        when(mockRepo.findByTenantId(tenantId, pageable)).thenReturn(page);
        when(mockRepo.findByTenantIdAndIsRead(tenantId, false, pageable)).thenReturn(page);
        when(mockRepo.findByTenantIdAndIsReadFalseOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(notification));
        when(mockRepo.countByTenantIdAndIsReadFalse(tenantId)).thenReturn(1L);

        assertThat(mockRepo.findByTenantId(tenantId, pageable)).isEqualTo(page);
        assertThat(mockRepo.findByTenantIdAndIsRead(tenantId, false, pageable)).isEqualTo(page);
        assertThat(mockRepo.findByTenantIdAndIsReadFalseOrderByCreatedAtDesc(tenantId)).containsExactly(notification);
        assertThat(mockRepo.countByTenantIdAndIsReadFalse(tenantId)).isEqualTo(1L);

        verify(mockRepo, times(1)).findByTenantId(tenantId, pageable);
        verify(mockRepo, times(1)).findByTenantIdAndIsRead(tenantId, false, pageable);
        verify(mockRepo, times(1)).findByTenantIdAndIsReadFalseOrderByCreatedAtDesc(tenantId);
        verify(mockRepo, times(1)).countByTenantIdAndIsReadFalse(tenantId);
    }
}
