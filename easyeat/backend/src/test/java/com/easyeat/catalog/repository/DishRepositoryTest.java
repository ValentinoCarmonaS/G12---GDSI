package com.easyeat.catalog.repository;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("DishRepository Contract and Query Verification Tests (T015, T028)")
class DishRepositoryTest {

    @Test
    @DisplayName("Should be an interface annotated with @Repository and extend JpaRepository<Dish, UUID>")
    void shouldBeProperJpaRepository() {
        Class<?> clazz = DishRepository.class;
        assertThat(clazz.isInterface()).isTrue();
        assertThat(clazz.isAnnotationPresent(Repository.class)).isTrue();
        assertThat(JpaRepository.class.isAssignableFrom(clazz)).isTrue();
    }

    @Test
    @DisplayName("Should declare tenant-scoped pagination and uniqueness query methods with expected signatures")
    void shouldDeclareRequiredMethodSignatures() throws NoSuchMethodException {
        // existsByTenantIdAndNameLowerTrimmed
        Method existsMethod = DishRepository.class.getMethod("existsByTenantIdAndNameLowerTrimmed", UUID.class, String.class);
        assertThat(existsMethod.getReturnType()).isEqualTo(boolean.class);
        assertThat(existsMethod.isAnnotationPresent(Query.class)).isTrue();
        Query existsQuery = existsMethod.getAnnotation(Query.class);
        assertThat(existsQuery.value()).contains("LOWER(TRIM(d.name)) = LOWER(TRIM(:name))");

        // existsByTenantIdAndNameLowerTrimmedAndIdNot
        Method existsNotMethod = DishRepository.class.getMethod("existsByTenantIdAndNameLowerTrimmedAndIdNot", UUID.class, String.class, UUID.class);
        assertThat(existsNotMethod.getReturnType()).isEqualTo(boolean.class);
        assertThat(existsNotMethod.isAnnotationPresent(Query.class)).isTrue();
        Query existsNotQuery = existsNotMethod.getAnnotation(Query.class);
        assertThat(existsNotQuery.value()).contains("d.id != :id");

        // findByTenantId with Pageable
        Method findTenantPagedMethod = DishRepository.class.getMethod("findByTenantId", UUID.class, Pageable.class);
        assertThat(findTenantPagedMethod.getReturnType()).isEqualTo(Page.class);

        // findByTenantIdAndStatus with Pageable
        Method findTenantStatusPagedMethod = DishRepository.class.getMethod("findByTenantIdAndStatus", UUID.class, DishStatus.class, Pageable.class);
        assertThat(findTenantStatusPagedMethod.getReturnType()).isEqualTo(Page.class);

        // findByIdAndTenantId
        Method findByIdAndTenantIdMethod = DishRepository.class.getMethod("findByIdAndTenantId", UUID.class, UUID.class);
        assertThat(findByIdAndTenantIdMethod.getReturnType()).isEqualTo(Optional.class);

        // existsByIdAndTenantId
        Method existsByIdAndTenantIdMethod = DishRepository.class.getMethod("existsByIdAndTenantId", UUID.class, UUID.class);
        assertThat(existsByIdAndTenantIdMethod.getReturnType()).isEqualTo(boolean.class);

        // findByTenantIdAndStatusAndIsActiveTrue (T028)
        Method findPublishedActiveMethod = DishRepository.class.getMethod("findByTenantIdAndStatusAndIsActiveTrue", UUID.class, DishStatus.class);
        assertThat(findPublishedActiveMethod.getReturnType()).isEqualTo(List.class);
    }

    @Test
    @DisplayName("Should allow mock invocations for all new query methods")
    void shouldBeInvocableViaMock() {
        DishRepository mockRepo = Mockito.mock(DishRepository.class);
        UUID tenantId = UUID.randomUUID();
        UUID dishId = UUID.randomUUID();
        String name = "  Pasta Carbonara  ";
        Pageable pageable = PageRequest.of(0, 20);

        Dish sampleDish = new Dish(tenantId, "Pasta Carbonara", "Rica pasta", new BigDecimal("3500.00"), false);
        sampleDish.setId(dishId);
        sampleDish.setCategory(DishCategory.PLATOS_PRINCIPALES);
        sampleDish.setStatus(DishStatus.DRAFT);

        when(mockRepo.existsByTenantIdAndNameLowerTrimmed(tenantId, name)).thenReturn(true);
        when(mockRepo.existsByTenantIdAndNameLowerTrimmedAndIdNot(tenantId, name, dishId)).thenReturn(false);
        when(mockRepo.findByTenantId(tenantId, pageable)).thenReturn(new PageImpl<>(List.of(sampleDish), pageable, 1));
        when(mockRepo.findByTenantIdAndStatus(tenantId, DishStatus.DRAFT, pageable)).thenReturn(new PageImpl<>(List.of(sampleDish), pageable, 1));
        when(mockRepo.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(sampleDish));
        when(mockRepo.existsByIdAndTenantId(dishId, tenantId)).thenReturn(true);
        when(mockRepo.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED)).thenReturn(List.of(sampleDish));

        assertThat(mockRepo.existsByTenantIdAndNameLowerTrimmed(tenantId, name)).isTrue();
        assertThat(mockRepo.existsByTenantIdAndNameLowerTrimmedAndIdNot(tenantId, name, dishId)).isFalse();
        assertThat(mockRepo.findByTenantId(tenantId, pageable).getContent()).containsExactly(sampleDish);
        assertThat(mockRepo.findByTenantIdAndStatus(tenantId, DishStatus.DRAFT, pageable).getContent()).containsExactly(sampleDish);
        assertThat(mockRepo.findByIdAndTenantId(dishId, tenantId)).contains(sampleDish);
        assertThat(mockRepo.existsByIdAndTenantId(dishId, tenantId)).isTrue();
        assertThat(mockRepo.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED)).containsExactly(sampleDish);

        verify(mockRepo, times(1)).existsByTenantIdAndNameLowerTrimmed(tenantId, name);
        verify(mockRepo, times(1)).existsByTenantIdAndNameLowerTrimmedAndIdNot(tenantId, name, dishId);
        verify(mockRepo, times(1)).findByTenantId(tenantId, pageable);
        verify(mockRepo, times(1)).findByTenantIdAndStatus(tenantId, DishStatus.DRAFT, pageable);
        verify(mockRepo, times(1)).findByIdAndTenantId(dishId, tenantId);
        verify(mockRepo, times(1)).existsByIdAndTenantId(dishId, tenantId);
        verify(mockRepo, times(1)).findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED);
    }
}
