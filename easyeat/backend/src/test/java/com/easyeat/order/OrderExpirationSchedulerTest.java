package com.easyeat.order;

import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.OrderExpirationScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderExpirationScheduler Unit Tests")
class OrderExpirationSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderExpirationScheduler scheduler;

    @Test
    @DisplayName("Transitions overdue PENDING_PAYMENT orders to EXPIRED during sweep")
    void sweepsAndTransitionsOverdueOrders() {
        Order order1 = new Order();
        order1.setId(UUID.randomUUID());
        order1.setStatus(OrderStatus.PENDING_PAYMENT);

        Order order2 = new Order();
        order2.setId(UUID.randomUUID());
        order2.setStatus(OrderStatus.PENDING_PAYMENT);

        when(orderRepository.findExpiredPendingOrders(any(Instant.class)))
                .thenReturn(List.of(order1, order2));

        scheduler.sweepExpiredOrders();

        assertThat(order1.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        verify(orderRepository).saveAll(List.of(order1, order2));
    }

    @Test
    @DisplayName("Does nothing when no pending orders are expired")
    void doesNothingWhenNoExpiredOrders() {
        when(orderRepository.findExpiredPendingOrders(any(Instant.class)))
                .thenReturn(List.of());

        scheduler.sweepExpiredOrders();

        verify(orderRepository, never()).saveAll(any());
    }
}
