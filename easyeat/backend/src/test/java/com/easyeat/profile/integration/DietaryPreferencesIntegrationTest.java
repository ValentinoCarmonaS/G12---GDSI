package com.easyeat.profile.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.profile.domain.UserDietaryRestriction;
import com.easyeat.profile.dto.DietaryPreferencesRequest;
import com.easyeat.profile.dto.DietaryRestrictionItemDto;
import com.easyeat.profile.exception.DietaryTaxonomyException;
import com.easyeat.profile.repository.DietaryRestrictionRepository;
import com.easyeat.profile.repository.ProfileUserRepository;
import com.easyeat.profile.repository.UserDietaryRestrictionRepository;
import com.easyeat.profile.service.DietaryRestrictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("DietaryPreferencesIntegrationTest (T030, COM-1.4.3)")
class DietaryPreferencesIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_dietary_it")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private DietaryRestrictionRepository dietaryRestrictionRepository;

    @Autowired
    private UserDietaryRestrictionRepository userDietaryRestrictionRepository;

    @Autowired
    private ProfileUserRepository profileUserRepository;

    @Autowired
    private DietaryRestrictionService dietaryRestrictionService;

    private User diner;

    @BeforeEach
    void setUp() {
        userDietaryRestrictionRepository.deleteAll();
        profileUserRepository.deleteAll();

        diner = new User("diner.dietary@example.com", "pwdHash123", UserRole.COMENSAL);
        diner.setFirstName("Valentino");
        diner.setLastName("Carmona");
        diner.setFullName("Valentino Carmona");
        diner.setStatus(UserStatus.ACTIVE);
        diner = profileUserRepository.save(diner);
    }

    @Test
    @DisplayName("Verifies Flyway seeded the 11 standardized platform restrictions")
    void flywayMigration_contains11StandardizedDietaryRestrictions() {
        List<DietaryRestrictionItemDto> taxonomy = dietaryRestrictionService.getDietaryTaxonomy();

        assertThat(taxonomy)
                .hasSize(11)
                .extracting(DietaryRestrictionItemDto::code)
                .contains(
                        "CELIAC", "VEGAN", "VEGETARIAN",
                        "DAIRY", "PEANUTS", "TREE_NUTS", "EGGS", "FISH", "SHELLFISH", "SOY", "WHEAT"
                );
    }

    @Test
    @DisplayName("Persists and updates user dietary preferences atomically")
    void updateUserPreferences_persistsAndReplacesAtomically() {
        // Step 1: Initial save
        DietaryPreferencesRequest req1 = new DietaryPreferencesRequest(List.of("CELIAC", "DAIRY"));
        List<DietaryRestrictionItemDto> saved1 = dietaryRestrictionService.updateUserPreferences(diner, req1);

        assertThat(saved1).hasSize(2);
        List<UserDietaryRestriction> inDb1 = userDietaryRestrictionRepository.findByIdUserId(diner.getId());
        assertThat(inDb1).hasSize(2);
        assertThat(inDb1).extracting(udr -> udr.getId().getRestrictionCode())
                .containsExactlyInAnyOrder("CELIAC", "DAIRY");

        // Step 2: Atomic update replacing with VEGAN
        DietaryPreferencesRequest req2 = new DietaryPreferencesRequest(List.of("VEGAN"));
        List<DietaryRestrictionItemDto> saved2 = dietaryRestrictionService.updateUserPreferences(diner, req2);

        assertThat(saved2).hasSize(1);
        assertThat(saved2.get(0).code()).isEqualTo("VEGAN");
        List<UserDietaryRestriction> inDb2 = userDietaryRestrictionRepository.findByIdUserId(diner.getId());
        assertThat(inDb2).hasSize(1);
        assertThat(inDb2.get(0).getId().getRestrictionCode()).isEqualTo("VEGAN");

        // Step 3: Deselect all (empty list)
        DietaryPreferencesRequest req3 = new DietaryPreferencesRequest(List.of());
        List<DietaryRestrictionItemDto> saved3 = dietaryRestrictionService.updateUserPreferences(diner, req3);

        assertThat(saved3).isEmpty();
        List<UserDietaryRestriction> inDb3 = userDietaryRestrictionRepository.findByIdUserId(diner.getId());
        assertThat(inDb3).isEmpty();
    }

    @Test
    @DisplayName("Rolls back entire transaction when invalid taxonomy code is provided")
    void updateUserPreferences_rollsBackOnInvalidCode() {
        // Setup initial valid preference
        dietaryRestrictionService.updateUserPreferences(diner, new DietaryPreferencesRequest(List.of("CELIAC")));
        assertThat(userDietaryRestrictionRepository.findByIdUserId(diner.getId())).hasSize(1);

        // Attempt update with one valid and one invalid code
        DietaryPreferencesRequest invalidReq = new DietaryPreferencesRequest(List.of("VEGAN", "INVALID_UNKNOWN_CODE"));

        assertThatThrownBy(() -> dietaryRestrictionService.updateUserPreferences(diner, invalidReq))
                .isInstanceOf(DietaryTaxonomyException.class)
                .hasMessageContaining("INVALID_UNKNOWN_CODE");

        // Verify state is strictly preserved (CELIAC remains, no VEGAN or partial state)
        List<UserDietaryRestriction> inDb = userDietaryRestrictionRepository.findByIdUserId(diner.getId());
        assertThat(inDb).hasSize(1);
        assertThat(inDb.get(0).getId().getRestrictionCode()).isEqualTo("CELIAC");
    }

    @Test
    @DisplayName("Cascade deletes user_dietary_restrictions when User entity is removed")
    void deleteUser_cascadeDeletesUserDietaryRestrictions() {
        dietaryPreferencesService_savePreferences("CELIAC", "PEANUTS");
        assertThat(userDietaryRestrictionRepository.findByIdUserId(diner.getId())).hasSize(2);

        profileUserRepository.deleteById(diner.getId());

        assertThat(userDietaryRestrictionRepository.findByIdUserId(diner.getId())).isEmpty();
    }

    private void dietaryPreferencesService_savePreferences(String... codes) {
        dietaryRestrictionService.updateUserPreferences(diner, new DietaryPreferencesRequest(List.of(codes)));
    }
}
