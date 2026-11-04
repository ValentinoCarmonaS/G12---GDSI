package com.easyeat.order;

import com.easyeat.order.controller.PickupSlotController;
import com.easyeat.order.dto.PickupSlotCatalogResponse;
import com.easyeat.order.service.PickupSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PickupSlotController Unit Tests (T034d)")
class PickupSlotControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PickupSlotService pickupSlotService;

    @InjectMocks
    private PickupSlotController pickupSlotController;

    private UUID establishmentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(pickupSlotController).build();
        establishmentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("T034d: GET /api/v1/establishments/{establishmentId}/pickup-slots returns 200 with slots catalog")
    void getPickupSlots_success() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 10);
        PickupSlotCatalogResponse response = new PickupSlotCatalogResponse(
                establishmentId,
                date,
                Instant.now(),
                "America/Argentina/Buenos_Aires",
                true,
                true,
                30,
                Collections.emptyList()
        );

        when(pickupSlotService.getPickupSlots(eq(establishmentId), eq(date))).thenReturn(response);

        mockMvc.perform(get("/api/v1/establishments/" + establishmentId + "/pickup-slots")
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishmentId").value(establishmentId.toString()))
                .andExpect(jsonPath("$.date").exists())
                .andExpect(jsonPath("$.leadTimeMinutes").value(30));

        verify(pickupSlotService).getPickupSlots(establishmentId, date);
    }

    @Test
    @DisplayName("T034d: PickupSlotController must not map '/{establishmentId}/time-slots' to avoid conflict with DinerTimeSlotController")
    void verifyNoTimeSlotsAliasCollision() throws NoSuchMethodException {
        Method method = PickupSlotController.class.getMethod("getPickupSlots", UUID.class, LocalDate.class);
        GetMapping getMapping = method.getAnnotation(GetMapping.class);

        assertThat(getMapping).isNotNull();
        String[] paths = getMapping.value().length > 0 ? getMapping.value() : getMapping.path();

        // Must only map pickup-slots, not time-slots which belongs to operations
        assertThat(paths)
                .as("PickupSlotController must only map pickup-slots, not time-slots which belongs to operations")
                .containsExactly("/{establishmentId}/pickup-slots");
    }
}