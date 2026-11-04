package com.easyeat.catalog.integration;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.service.DishService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T039: DinerCatalogAvailabilityPollingTest (INV-15)")
class DinerCatalogAvailabilityPollingTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_test")
            .withUsername("easyeat_test")
            .withPassword("easyeat_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private DishService dishService;

    private UUID tenantId;
    private Dish dish;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        Establishment est = new Establishment();
        est.setId(tenantId);
        est.setName("Resto Polling");
        est.setStatus(EstablishmentStatus.ACTIVE);
        establishmentRepository.save(est);

        dish = new Dish();
        dish.setTenantId(tenantId);
        dish.setName("Plato Polling");
        dish.setDescription("Desc");
        dish.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dish.setPrice(BigDecimal.valueOf(2000.00));
        dish.setIsActive(true);
        dish.setStatus(DishStatus.PUBLISHED);
        dish.setPausedByChef(false);
        dish.setCreatedAt(Instant.now());
        dish.setUpdatedAt(Instant.now());
        dish = dishRepository.save(dish);
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("testPropagationLatency: mutacion de pausa cambia ETag -> 200 en siguiente polling, 304 cuando no hay cambio (INV-15)")
    void testPropagationLatency() throws Exception {
        MvcResult firstResult = mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId))
                .andExpect(status().isOk())
                .andReturn();

        String etag = firstResult.getResponse().getHeader("ETag");
        assertThat(etag).isNotNull();

        // 304 Not Modified con el mismo ETag
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified());

        // Chef conmuta pausa
        dishService.toggleChefPause(dish.getId(), true, tenantId);

        // Polling con el viejo ETag ahora debe retornar 200 con un nuevo ETag
        MvcResult updatedResult = mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                        .header("If-None-Match", etag))
                .andExpect(status().isOk())
                .andReturn();

        String newEtag = updatedResult.getResponse().getHeader("ETag");
        assertThat(newEtag).isNotNull().isNotEqualTo(etag);
    }
}
