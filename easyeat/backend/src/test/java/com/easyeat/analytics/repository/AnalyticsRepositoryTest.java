package com.easyeat.analytics.repository;

import com.easyeat.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnalyticsRepositoryTest (T007, spec 023): concrete statuses and [start, end) half-open interval")
class AnalyticsRepositoryTest {

    @Test
    @DisplayName("COMPLETED_ORDER_STATUSES contains exactly PAID, IN_PROGRESS, READY_FOR_PICKUP, COMPLETED")
    void completedOrderStatusesConstant() {
        assertThat(AnalyticsRepository.COMPLETED_ORDER_STATUSES)
                .containsExactlyInAnyOrder(
                        OrderStatus.PAID,
                        OrderStatus.IN_PROGRESS,
                        OrderStatus.READY_FOR_PICKUP,
                        OrderStatus.COMPLETED
                );
    }

    @Test
    @DisplayName("aggregateFinancialHealth query uses half-open interval [start, end) on paidAt")
    void aggregateFinancialHealthUsesHalfOpenInterval() throws NoSuchMethodException {
        Method method = AnalyticsRepository.class.getMethod(
                "aggregateFinancialHealth", UUID.class, Set.class, Instant.class, Instant.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("o.paidAt >= :start");
        assertThat(query.value()).contains("o.paidAt < :end");
        assertThat(query.value()).contains("o.status IN :statuses");
    }

    @Test
    @DisplayName("findDishRanking query orders by unitsSold DESC, LOWER(dishName) ASC")
    void findDishRankingOrdersDeterministically() throws NoSuchMethodException {
        Method method = AnalyticsRepository.class.getMethod(
                "findDishRanking", UUID.class, Set.class, Instant.class, Instant.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("ORDER BY SUM(oi.quantity) DESC, LOWER(oi.dishName) ASC");
    }
}
