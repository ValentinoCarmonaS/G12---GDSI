package com.easyeat.order.repository;

import java.time.LocalTime;

/**
 * Spring Data JPA projection for grouped time-slot occupancy counts (FR-003, D-03).
 */
public interface TimeSlotOccupancyProjection {
    LocalTime getTimeSlotStart();
    long getBookedCount();
}
