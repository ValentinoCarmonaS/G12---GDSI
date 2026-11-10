package com.easyeat.profile.migration;

import com.easyeat.profile.domain.DietaryCategory;
import com.easyeat.profile.domain.DietaryRestriction;
import com.easyeat.profile.dto.DietaryRestrictionItemDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class C11CeliacReclassificationMigrationTest {

    @Test
    @DisplayName("C-11: Flyway V19 script exists and executes DML update from DIETARY_LIFESTYLE to COMMON_ALLERGEN")
    void v19MigrationScript_containsDeterministicUpdate() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/V19__reclassify_celiac_as_common_allergen.sql");
        assertThat(resource.exists()).isTrue();

        try (InputStream is = resource.getInputStream()) {
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).containsIgnoringCase("UPDATE dietary_restrictions");
            assertThat(sql).containsIgnoringCase("category = 'COMMON_ALLERGEN'");
            assertThat(sql).containsIgnoringCase("WHERE code = 'CELIAC'");
        }
    }

    @Test
    @DisplayName("C-11: Seed V5 registers CELIAC as COMMON_ALLERGEN for fresh DB setups")
    void v5SeedScript_registersCeliacAsCommonAllergen() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/V5__extend_users_profile_and_dietary.sql");
        assertThat(resource.exists()).isTrue();

        try (InputStream is = resource.getInputStream()) {
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("('CELIAC', 'Sin TACC / Celíaco', 'COMMON_ALLERGEN'");
        }
    }

    @Test
    @DisplayName("C-11: DietaryRestriction entity maps CELIAC to COMMON_ALLERGEN in DTO serialization")
    void entityAndDto_serializeCeliacAsCommonAllergen() {
        DietaryRestriction entity = new DietaryRestriction(
                "CELIAC",
                "Sin TACC / Celíaco",
                DietaryCategory.COMMON_ALLERGEN,
                "Excluye trigo, avena, cebada y centeno."
        );

        DietaryRestrictionItemDto dto = DietaryRestrictionItemDto.fromEntity(entity);

        assertThat(dto.code()).isEqualTo("CELIAC");
        assertThat(dto.category()).isEqualTo("COMMON_ALLERGEN");
        assertThat(dto.name()).isEqualTo("Sin TACC / Celíaco");
    }
}
