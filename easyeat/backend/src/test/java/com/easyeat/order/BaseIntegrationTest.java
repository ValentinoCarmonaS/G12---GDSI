package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.repository.OrderItemRepository;
import com.easyeat.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_order_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EstablishmentRepository establishmentRepository;

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected OrderItemRepository orderItemRepository;

    @Autowired
    protected DishRepository dishRepository;

    @Autowired
    protected UserRepository userRepository;

    protected Establishment createTestEstablishment(String name) {
        Establishment establishment = new Establishment(
                null,
                name,
                EstablishmentStatus.ACTIVE,
                LocalTime.of(11, 0),
                LocalTime.of(23, 0),
                "America/Argentina/Buenos_Aires"
        );
        establishment.setLeadTimeMinutes(30);
        establishment.setMaxCapacityPerSlot(10);
        establishment.setOperatingDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY");
        return establishmentRepository.save(establishment);
    }

    protected User createTestDiner(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Test Diner");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyz1234567890ABCDEF");
        user.setRole(UserRole.COMENSAL);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    protected Dish createTestDish(UUID tenantId, String name, BigDecimal price) {
        Dish dish = new Dish();
        dish.setTenantId(tenantId);
        dish.setName(name);
        dish.setPrice(price);
        dish.setRetailPrice(price);
        dish.setStatus(com.easyeat.dish.domain.DishStatus.PUBLISHED);
        return dishRepository.save(dish);
    }
}
