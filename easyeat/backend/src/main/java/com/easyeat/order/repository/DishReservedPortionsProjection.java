package com.easyeat.order.repository;

import java.util.UUID;

public interface DishReservedPortionsProjection {
    UUID getDishId();
    Long getReservedPortions();
}
