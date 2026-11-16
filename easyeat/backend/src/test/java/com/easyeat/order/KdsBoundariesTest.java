package com.easyeat.order;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.dto.KdsDelayIncrementResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.KdsQueueServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("T026 [Polish]: KdsBoundariesTest - Technical Domain Isolation & Immutability (Spec 021)")
class KdsBoundariesTest {

    @Mock
    private KdsTicketRepository kdsTicketRepository;

    @Mock
    private EstablishmentRepository establishmentRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private KdsQueueServiceImpl kdsQueueService;

    private UUID ticketId;
    private UUID orderId;
    private UUID establishmentId;
    private Establishment establishment;
    private Instant immutableStartedAt;
    private Instant immutableQueuedAt;
    private KdsTicket ticket;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        establishmentId = UUID.randomUUID();

        establishment = new Establishment();
        establishment.setId(establishmentId);

        immutableQueuedAt = Instant.parse("2026-11-16T12:00:00Z");
        immutableStartedAt = Instant.parse("2026-11-16T12:05:00Z");

        ticket = new KdsTicket(
                ticketId,
                orderId,
                establishment,
                101,
                KdsTicketStatus.IN_PREPARATION,
                FulfillmentMode.IMMEDIATE,
                LocalDate.of(2026, 11, 16),
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                immutableQueuedAt
        );
        ticket.setStartedAt(immutableStartedAt);
        ticket.setReadyAt(null);
        ticket.setSpecificDelayMinutes(0);
    }

    @Test
    @DisplayName("addTicketDelay muta estrictamente specificDelayMinutes (+5) sin alterar startedAt ni readyAt (RF-012, T026)")
    void addTicketDelayStrictlyMutatesSpecificDelayWithoutAlteringStartedAtNorReadyAt() {
        when(kdsTicketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(kdsTicketRepository.save(any(KdsTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KdsDelayIncrementResponse response = kdsQueueService.addTicketDelay(ticketId, establishmentId);

        assertThat(response).isNotNull();
        assertThat(response.specificDelayMinutes()).isEqualTo(5);
        assertThat(response.estimatedTotalMinutes()).isEqualTo(25);

        ArgumentCaptor<KdsTicket> ticketCaptor = ArgumentCaptor.forClass(KdsTicket.class);
        verify(kdsTicketRepository).save(ticketCaptor.capture());

        KdsTicket saved = ticketCaptor.getValue();
        // Verificación de aislamiento estricto de fronteras
        assertThat(saved.getSpecificDelayMinutes()).isEqualTo(5);
        assertThat(saved.getStartedAt()).isEqualTo(immutableStartedAt); // Inmutable para el cronómetro Count-Up
        assertThat(saved.getReadyAt()).isNull(); // Inmutable para la base de mostrador
        assertThat(saved.getQueuedAt()).isEqualTo(immutableQueuedAt); // Inmutable
        assertThat(saved.getStatus()).isEqualTo(KdsTicketStatus.IN_PREPARATION); // Sin cambio de estado
        assertThat(saved.getTicketNumber()).isEqualTo(101);
        assertThat(saved.getOrderId()).isEqualTo(orderId);

        // Sin efectos colaterales en la orden
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("Pulsaciones sucesivas de demora preservan startedAt inmutable como base del Count-Up")
    void successiveDelaysPreserveStartedAtAsImmutableCountUpBase() {
        when(kdsTicketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(kdsTicketRepository.save(any(KdsTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Primera adición (+5)
        kdsQueueService.addTicketDelay(ticketId, establishmentId);
        assertThat(ticket.getSpecificDelayMinutes()).isEqualTo(5);
        assertThat(ticket.getStartedAt()).isEqualTo(immutableStartedAt);

        // Segunda adición (+10 acumulado)
        kdsQueueService.addTicketDelay(ticketId, establishmentId);
        assertThat(ticket.getSpecificDelayMinutes()).isEqualTo(10);
        assertThat(ticket.getStartedAt()).isEqualTo(immutableStartedAt);
        assertThat(ticket.getReadyAt()).isNull();

        verify(kdsTicketRepository, times(2)).save(ticket);
        verifyNoInteractions(orderRepository);
    }
}
