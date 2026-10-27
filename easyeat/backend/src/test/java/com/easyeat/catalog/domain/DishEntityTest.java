package com.easyeat.catalog.domain;

import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Dish Entity Tests (T010 - Domain Model §1.1)")
class DishEntityTest {

    @Test
    @DisplayName("Default constructor sets default lifecycle status to DRAFT and allergensConfirmed to false")
    void defaultConstructor_initializesDefaults() {
        Dish dish = new Dish();

        assertThat(dish.getStatus()).isEqualTo(DishStatus.DRAFT);
        assertThat(dish.getAllergensConfirmed()).isFalse();
        assertThat(dish.isAllergensConfirmed()).isFalse();
        assertThat(dish.getIsActive()).isTrue();
        assertThat(dish.getCreatedAt()).isNotNull();
        assertThat(dish.getUpdatedAt()).isNotNull();
        assertThat(dish.getRestrictionCodes()).isNotNull().isEmpty();
        assertThat(dish.getPrice()).isNull();
        assertThat(dish.getCategory()).isNull();
        assertThat(dish.getCreatedBy()).isNull();
    }

    @Test
    @DisplayName("Legacy 5-arg constructor preserves backward compatibility and sets DRAFT and allergensConfirmed=false")
    void legacyConstructor_setsFieldsAndDefaults() {
        UUID tenantId = UUID.randomUUID();
        String name = "Lasaña Boloñesa";
        String description = "Lasaña casera";
        BigDecimal price = new BigDecimal("8500.00");

        Dish dish = new Dish(tenantId, name, description, price, true);

        assertThat(dish.getTenantId()).isEqualTo(tenantId);
        assertThat(dish.getName()).isEqualTo(name);
        assertThat(dish.getDescription()).isEqualTo(description);
        assertThat(dish.getPrice()).isEqualTo(price);
        assertThat(dish.getIsActive()).isTrue();
        assertThat(dish.getStatus()).isEqualTo(DishStatus.DRAFT);
        assertThat(dish.getAllergensConfirmed()).isFalse();
        assertThat(dish.getCreatedAt()).isNotNull();
        assertThat(dish.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Full constructor initializes all fields including category, status, creator, and nullable price")
    void fullConstructor_initializesAllFields() {
        UUID tenantId = UUID.randomUUID();
        UUID chefId = UUID.randomUUID();
        String name = "Risotto de Hongos";
        DishCategory category = DishCategory.PLATOS_PRINCIPALES;
        String description = "Arroz cremoso con portobellos";
        BigDecimal price = null; // Drafts can have null price

        Dish dish = new Dish(tenantId, name, category, description, price, false, chefId);

        assertThat(dish.getTenantId()).isEqualTo(tenantId);
        assertThat(dish.getName()).isEqualTo(name);
        assertThat(dish.getCategory()).isEqualTo(category);
        assertThat(dish.getDescription()).isEqualTo(description);
        assertThat(dish.getPrice()).isNull();
        assertThat(dish.getIsActive()).isFalse();
        assertThat(dish.getCreatedBy()).isEqualTo(chefId);
        assertThat(dish.getStatus()).isEqualTo(DishStatus.DRAFT);
        assertThat(dish.getAllergensConfirmed()).isFalse();

        // When isActive is passed as null, defaults to false
        Dish dishNullActive = new Dish(tenantId, name, category, description, price, null, chefId);
        assertThat(dishNullActive.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Getters and Setters modify entity state properly for new columns")
    void gettersAndSetters_modifyState() {
        Dish dish = new Dish();
        UUID dishId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID chefId = UUID.randomUUID();
        Instant now = Instant.now();

        dish.setId(dishId);
        dish.setTenantId(tenantId);
        dish.setName("Ceviche Clásico");
        dish.setDescription("Pescado fresco marinado en lima");
        dish.setCategory(DishCategory.ENTRADAS);
        dish.setPrice(new BigDecimal("9200.50"));
        dish.setStatus(DishStatus.PENDING_PRICE);
        dish.setAllergensConfirmed(true);
        dish.setCreatedBy(chefId);
        dish.setIsActive(true);
        dish.setRestrictionCodes(Set.of("PESCADO", "CITRICOS"));
        dish.setCreatedAt(now);
        dish.setUpdatedAt(now);

        assertThat(dish.getId()).isEqualTo(dishId);
        assertThat(dish.getTenantId()).isEqualTo(tenantId);
        assertThat(dish.getName()).isEqualTo("Ceviche Clásico");
        assertThat(dish.getDescription()).isEqualTo("Pescado fresco marinado en lima");
        assertThat(dish.getCategory()).isEqualTo(DishCategory.ENTRADAS);
        assertThat(dish.getPrice()).isEqualTo(new BigDecimal("9200.50"));
        assertThat(dish.getStatus()).isEqualTo(DishStatus.PENDING_PRICE);
        assertThat(dish.getAllergensConfirmed()).isTrue();
        assertThat(dish.isAllergensConfirmed()).isTrue();
        assertThat(dish.getCreatedBy()).isEqualTo(chefId);
        assertThat(dish.getIsActive()).isTrue();
        assertThat(dish.getRestrictionCodes()).containsExactlyInAnyOrder("PESCADO", "CITRICOS");
        assertThat(dish.getCreatedAt()).isEqualTo(now);
        assertThat(dish.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("JPA mapping annotations comply with specification 005 data-model §1.1")
    void jpaAnnotations_matchSpecification() throws NoSuchFieldException {
        // Table annotation
        Table tableAnnotation = Dish.class.getAnnotation(Table.class);
        assertThat(tableAnnotation).isNotNull();
        assertThat(tableAnnotation.name()).isEqualTo("dishes");

        // name field: length = 100, nullable = false
        Field nameField = Dish.class.getDeclaredField("name");
        Column nameColumn = nameField.getAnnotation(Column.class);
        assertThat(nameColumn).isNotNull();
        assertThat(nameColumn.nullable()).isFalse();
        assertThat(nameColumn.length()).isEqualTo(100);

        // price field: nullable = true, precision = 10, scale = 2
        Field priceField = Dish.class.getDeclaredField("price");
        Column priceColumn = priceField.getAnnotation(Column.class);
        assertThat(priceColumn).isNotNull();
        assertThat(priceColumn.nullable()).isTrue();
        assertThat(priceColumn.precision()).isEqualTo(10);
        assertThat(priceColumn.scale()).isEqualTo(2);

        // category field: length = 50
        Field categoryField = Dish.class.getDeclaredField("category");
        Column categoryColumn = categoryField.getAnnotation(Column.class);
        assertThat(categoryColumn).isNotNull();
        assertThat(categoryColumn.name()).isEqualTo("category");
        assertThat(categoryColumn.length()).isEqualTo(50);

        // status field: @Enumerated(EnumType.STRING), nullable = false, length = 50
        Field statusField = Dish.class.getDeclaredField("status");
        Enumerated enumerated = statusField.getAnnotation(Enumerated.class);
        assertThat(enumerated).isNotNull();
        assertThat(enumerated.value()).isEqualTo(EnumType.STRING);
        Column statusColumn = statusField.getAnnotation(Column.class);
        assertThat(statusColumn).isNotNull();
        assertThat(statusColumn.nullable()).isFalse();
        assertThat(statusColumn.length()).isEqualTo(50);

        // allergens_confirmed field: nullable = false
        Field allergensField = Dish.class.getDeclaredField("allergensConfirmed");
        Column allergensColumn = allergensField.getAnnotation(Column.class);
        assertThat(allergensColumn).isNotNull();
        assertThat(allergensColumn.name()).isEqualTo("allergens_confirmed");
        assertThat(allergensColumn.nullable()).isFalse();

        // created_by field: nullable = true
        Field createdByField = Dish.class.getDeclaredField("createdBy");
        Column createdByColumn = createdByField.getAnnotation(Column.class);
        assertThat(createdByColumn).isNotNull();
        assertThat(createdByColumn.name()).isEqualTo("created_by");
    }
}
