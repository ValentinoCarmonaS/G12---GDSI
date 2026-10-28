package com.easyeat.dish;

import com.easyeat.catalog.domain.Dish;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DishTestFixturesTest {

    @Test
    @DisplayName("Should provide consistent default UUID identifiers")
    void defaultIdentifiers() {
        assertThat(DishTestFixtures.DEFAULT_TENANT_ID).isNotNull();
        assertThat(DishTestFixtures.ANOTHER_TENANT_ID).isNotNull();
        assertThat(DishTestFixtures.DEFAULT_CHEF_ID).isNotNull();
        assertThat(DishTestFixtures.DEFAULT_ADMIN_ID).isNotNull();
        assertThat(DishTestFixtures.DEFAULT_DISH_ID).isNotNull();
        assertThat(DishTestFixtures.DEFAULT_INGREDIENT_ID).isNotNull();

        assertThat(DishTestFixtures.DEFAULT_TENANT_ID).isNotEqualTo(DishTestFixtures.ANOTHER_TENANT_ID);
    }

    @Test
    @DisplayName("Should create standard Dish instance with valid defaults")
    void createStandardDish() {
        Dish dish = DishTestFixtures.createStandardDish();

        assertThat(dish).isNotNull();
        assertThat(dish.getTenantId()).isEqualTo(DishTestFixtures.DEFAULT_TENANT_ID);
        assertThat(dish.getName()).isEqualTo(DishTestFixtures.DEFAULT_DISH_NAME);
        assertThat(dish.getDescription()).isEqualTo(DishTestFixtures.DEFAULT_DISH_DESCRIPTION);
        assertThat(dish.getPrice()).isEqualTo(BigDecimal.valueOf(4500.00));
        assertThat(dish.getIsActive()).isTrue();
        assertThat(dish.getCreatedAt()).isNotNull();
        assertThat(dish.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should create Dish via fluent builder with custom overrides")
    void dishBuilder() {
        UUID customTenant = UUID.randomUUID();
        Dish dish = DishTestFixtures.dishBuilder()
                .tenantId(customTenant)
                .name("Tarta Rústica de Calabaza")
                .description("Calabaza asada con hierbas finas")
                .price(BigDecimal.valueOf(3200.50))
                .isActive(true)
                .restrictions(Set.of("VEGETARIAN"))
                .build();

        assertThat(dish.getTenantId()).isEqualTo(customTenant);
        assertThat(dish.getName()).isEqualTo("Tarta Rústica de Calabaza");
        assertThat(dish.getDescription()).isEqualTo("Calabaza asada con hierbas finas");
        assertThat(dish.getPrice()).isEqualTo(BigDecimal.valueOf(3200.50));
        assertThat(dish.getRestrictionCodes()).containsExactly("VEGETARIAN");
    }

    @Test
    @DisplayName("Should generate valid photo byte arrays matching JPEG, PNG and WebP magic numbers")
    void validPhotoFixtures() {
        byte[] jpeg = DishTestFixtures.createValidJpegBytes();
        assertThat(jpeg).isNotEmpty();
        assertThat(jpeg.length).isLessThanOrEqualTo(5 * 1024 * 1024);
        assertThat(DishTestFixtures.isJpeg(jpeg)).isTrue();

        byte[] png = DishTestFixtures.createValidPngBytes();
        assertThat(png).isNotEmpty();
        assertThat(DishTestFixtures.isPng(png)).isTrue();

        byte[] webp = DishTestFixtures.createValidWebpBytes();
        assertThat(webp).isNotEmpty();
        assertThat(DishTestFixtures.isWebp(webp)).isTrue();
    }

    @Test
    @DisplayName("Should generate oversized photo fixture exceeding 5 MB limit")
    void oversizedPhotoFixture() {
        byte[] oversized = DishTestFixtures.createOversizedPhotoBytes();
        assertThat(oversized.length).isGreaterThan(5 * 1024 * 1024);
    }

    @Test
    @DisplayName("Should generate invalid format photo bytes")
    void invalidPhotoFixture() {
        byte[] invalid = DishTestFixtures.createInvalidFormatBytes();
        assertThat(invalid).isNotEmpty();
        assertThat(DishTestFixtures.isJpeg(invalid)).isFalse();
        assertThat(DishTestFixtures.isPng(invalid)).isFalse();
        assertThat(DishTestFixtures.isWebp(invalid)).isFalse();
    }
}
