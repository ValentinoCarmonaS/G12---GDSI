package com.easyeat.dish.acceptance;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.CreateDishRequest;
import com.easyeat.dish.dto.DishDetailResponse;
import com.easyeat.dish.dto.DishPageResponse;
import com.easyeat.dish.dto.PhotoUploadResponse;
import com.easyeat.dish.dto.UpdateDishRequest;
import com.easyeat.dish.exception.DuplicateDishNameException;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.service.DishPhotoService;
import com.easyeat.dish.service.DishService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("User Story 1 Acceptance Test: Basic Dish Identity & Photo (T022, CHF-2.2.1)")
class DishBasicAcceptanceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_dish_us1_acceptance")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
    }

    @Autowired
    private DishService dishService;

    @Autowired
    private DishPhotoService dishPhotoService;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private DishPhotoRepository dishPhotoRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID chefId = DishTestFixtures.DEFAULT_CHEF_ID;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, role, status, tenant_id, token_version, created_at, updated_at)
                VALUES (?, 'chef.acceptance@easyeat.com', '$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2', 'CHEF', 'ACTIVE', ?, 0, NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, chefId, tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("US1 Full Acceptance Lifecycle: Create Draft -> Upload Photo -> Edit -> List -> Delete Draft")
    void fullUserStory1AcceptanceLifecycle() {
        // 1. Create a dish draft with whitespace-padded name and category
        String rawName = "   Ceviche Mixto Tradicional   ";
        CreateDishRequest createRequest = new CreateDishRequest(
                rawName,
                DishCategory.ENTRADAS,
                "Pescado blanco y mariscos marinados en jugo de lima con cebolla morada."
        );

        DishDetailResponse created = dishService.createDraft(createRequest, chefId);

        assertThat(created).isNotNull();
        assertThat(created.name()).isEqualTo("Ceviche Mixto Tradicional");
        assertThat(created.category()).isEqualTo(DishCategory.ENTRADAS);
        assertThat(created.status()).isEqualTo(DishStatus.DRAFT);
        assertThat(created.allergensConfirmed()).isFalse();

        UUID dishId = created.id();

        // 2. Verify duplicate name rejection within the same establishment (case-insensitive and trimmed)
        CreateDishRequest duplicateRequest = new CreateDishRequest(
                "ceviche mixto tradicional",
                DishCategory.ENTRADAS,
                "Otra versión del ceviche"
        );
        assertThatThrownBy(() -> dishService.createDraft(duplicateRequest, chefId))
                .isInstanceOf(DuplicateDishNameException.class);

        // 3. Upload a valid JPEG photo to the draft
        byte[] photoData = DishTestFixtures.createValidJpegBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "ceviche.jpg", "image/jpeg", photoData
        );
        PhotoUploadResponse uploadResponse = dishPhotoService.uploadPhoto(dishId, file);

        assertThat(uploadResponse).isNotNull();
        assertThat(uploadResponse.dishId()).isEqualTo(dishId);
        assertThat(uploadResponse.photoUrl()).isEqualTo("/api/v1/dishes/" + dishId + "/photo");

        Optional<DishPhoto> storedPhoto = dishPhotoRepository.findByDishId(dishId);
        assertThat(storedPhoto).isPresent();
        assertThat(storedPhoto.get().getContentType()).isEqualTo("image/jpeg");

        // 4. Update basic identity fields
        UpdateDishRequest updateRequest = new UpdateDishRequest(
                "Ceviche Clásico Peruano",
                DishCategory.ENTRADAS,
                "Pescado fresco del día con ají limo y cilantro."
        );
        DishDetailResponse updated = dishService.updateBasicInfo(dishId, updateRequest);
        assertThat(updated.name()).isEqualTo("Ceviche Clásico Peruano");
        assertThat(updated.status()).isEqualTo(DishStatus.DRAFT);

        // 5. Verify the dish draft appears in the Chef's paginated listing
        DishPageResponse page = dishService.listDishes("DRAFT", PageRequest.of(0, 20));
        assertThat(page.content()).anyMatch(d -> d.id().equals(dishId) && d.name().equals("Ceviche Clásico Peruano"));

        // 6. Delete photo from draft
        dishPhotoService.deletePhoto(dishId);
        assertThat(dishPhotoRepository.findByDishId(dishId)).isEmpty();

        // 7. Delete the entire dish draft
        dishService.deleteDraft(dishId);
        assertThat(dishRepository.findByIdAndTenantId(dishId, tenantId)).isEmpty();
    }
}
