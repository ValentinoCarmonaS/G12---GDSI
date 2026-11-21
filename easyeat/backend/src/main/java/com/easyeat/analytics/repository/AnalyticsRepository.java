package com.easyeat.analytics.repository;

import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface AnalyticsRepository extends JpaRepository<Order, UUID> {

    Set<OrderStatus> COMPLETED_ORDER_STATUSES = Set.of(
            OrderStatus.PAID,
            OrderStatus.IN_PROGRESS,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.COMPLETED
    );

    interface FinancialHealthProjection {
        BigDecimal getRevenue();
        Long getOrderCount();
    }

    interface DishRankingProjection {
        UUID getDishId();
        String getDishName();
        Long getUnitsSold();
    }

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) AS revenue, COUNT(o.id) AS orderCount
            FROM Order o
            WHERE o.establishmentId = :establishmentId
              AND o.status IN :statuses
              AND o.paidAt >= :start
              AND o.paidAt < :end
            """)
    FinancialHealthProjection aggregateFinancialHealth(
            @Param("establishmentId") UUID establishmentId,
            @Param("statuses") Set<OrderStatus> statuses,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("""
            SELECT oi.dishId AS dishId, oi.dishName AS dishName, SUM(oi.quantity) AS unitsSold
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.establishmentId = :establishmentId
              AND o.status IN :statuses
            GROUP BY oi.dishId, oi.dishName
            HAVING SUM(oi.quantity) > 0
            ORDER BY SUM(oi.quantity) DESC, LOWER(oi.dishName) ASC
            """)
    List<DishRankingProjection> findDishRankingAll(
            @Param("establishmentId") UUID establishmentId,
            @Param("statuses") Set<OrderStatus> statuses
    );

    @Query("""
            SELECT oi.dishId AS dishId, oi.dishName AS dishName, SUM(oi.quantity) AS unitsSold
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.establishmentId = :establishmentId
              AND o.status IN :statuses
              AND o.paidAt >= :start
              AND o.paidAt < :end
            GROUP BY oi.dishId, oi.dishName
            HAVING SUM(oi.quantity) > 0
            ORDER BY SUM(oi.quantity) DESC, LOWER(oi.dishName) ASC
            """)
    List<DishRankingProjection> findDishRanking(
            @Param("establishmentId") UUID establishmentId,
            @Param("statuses") Set<OrderStatus> statuses,
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
