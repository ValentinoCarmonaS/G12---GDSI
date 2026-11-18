package com.easyeat.order.dto;

import com.easyeat.order.domain.OrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CounterReadyOrderResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("CounterReadyOrderResponse serializes with HH:mm timeSlot, nullable photoUrl, 4-digit securityCode, items, and zero monetary fields")
    void shouldSerializeCounterReadyOrderResponseMatchesContractAndOmitsMonetaryFields() throws Exception {
        UUID ticketId = UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d");
        UUID orderId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        UUID userId = UUID.fromString("c71e2289-4b68-45e0-a7d1-e6e7d6928e01");
        Instant readyAt = Instant.parse("2026-11-15T19:30:00Z");

        CounterReadyOrderResponse response = new CounterReadyOrderResponse(
                ticketId,
                orderId,
                104,
                new CounterReadyOrderResponse.TimeSlotDto(LocalTime.of(13, 0), LocalTime.of(13, 15)),
                readyAt,
                new CounterReadyOrderResponse.CustomerSummaryDto(
                        userId,
                        "Valentino Carmona",
                        "/api/v1/profile/users/" + userId + "/avatar"
                ),
                "4821",
                List.of(
                        new CounterReadyOrderResponse.CounterOrderItemDto(2, "Hamburguesa Completa con Papas"),
                        new CounterReadyOrderResponse.CounterOrderItemDto(1, "Limonada con Menta y Jengibre")
                )
        );

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("ticketId").asText()).isEqualTo(ticketId.toString());
        assertThat(root.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(root.get("ticketNumber").asInt()).isEqualTo(104);
        assertThat(root.get("timeSlot").get("start").asText()).isEqualTo("13:00");
        assertThat(root.get("timeSlot").get("end").asText()).isEqualTo("13:15");
        assertThat(root.get("readyAt").asText()).isEqualTo("2026-11-15T19:30:00Z");
        assertThat(root.get("customer").get("userId").asText()).isEqualTo(userId.toString());
        assertThat(root.get("customer").get("fullName").asText()).isEqualTo("Valentino Carmona");
        assertThat(root.get("customer").get("photoUrl").asText())
                .isEqualTo("/api/v1/profile/users/" + userId + "/avatar");
        assertThat(root.get("securityCode").asText()).isEqualTo("4821");
        assertThat(root.get("items")).hasSize(2);
        assertThat(root.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(root.get("items").get(0).get("dishName").asText()).isEqualTo("Hamburguesa Completa con Papas");

        // RF-013 & Invariante 10: Strict absence of monetary fields
        Set<String> componentNames = Arrays.stream(CounterReadyOrderResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertThat(componentNames).doesNotContain("price", "unitPrice", "subtotal", "totalAmount", "total", "currency");
        assertThat(json).doesNotContain("unitPrice", "subtotal", "totalAmount", "price");
    }

    @Test
    @DisplayName("KdsTicketResponse exposes required attributes for atomic ready-for-pickup response")
    void shouldValidateKdsTicketResponseReuse() {
        UUID ticketId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant readyAt = Instant.parse("2026-11-15T19:30:00.123456Z");

        KdsTicketResponse response = new KdsTicketResponse(
                ticketId,
                orderId,
                104,
                OrderStatus.READY_FOR_PICKUP,
                readyAt
        );

        assertThat(response.ticketId()).isEqualTo(ticketId);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.ticketNumber()).isEqualTo(104);
        assertThat(response.status()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(response.readyAt()).isEqualTo(readyAt);
    }
}
