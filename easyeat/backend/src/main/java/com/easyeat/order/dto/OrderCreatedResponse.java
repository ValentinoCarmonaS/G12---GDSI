package com.easyeat.order.dto;

import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public class OrderCreatedResponse {

    private UUID id;
    private UUID establishmentId;
    private FulfillmentMode fulfillmentMode;
    private LocalDate pickupDate;
    private LocalTime timeSlotStart;
    private LocalTime timeSlotEnd;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private Instant expiresAt;
    private Long remainingSeconds;
    private Instant createdAt;

    public OrderCreatedResponse() {
    }

    public OrderCreatedResponse(UUID id,
                                UUID establishmentId,
                                FulfillmentMode fulfillmentMode,
                                LocalDate pickupDate,
                                LocalTime timeSlotStart,
                                LocalTime timeSlotEnd,
                                OrderStatus status,
                                BigDecimal totalAmount,
                                Instant expiresAt,
                                Long remainingSeconds,
                                Instant createdAt) {
        this.id = id;
        this.establishmentId = establishmentId;
        this.fulfillmentMode = fulfillmentMode;
        this.pickupDate = pickupDate;
        this.timeSlotStart = timeSlotStart;
        this.timeSlotEnd = timeSlotEnd;
        this.status = status;
        this.totalAmount = totalAmount;
        this.expiresAt = expiresAt;
        this.remainingSeconds = remainingSeconds;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public void setEstablishmentId(UUID establishmentId) {
        this.establishmentId = establishmentId;
    }

    public FulfillmentMode getFulfillmentMode() {
        return fulfillmentMode;
    }

    public void setFulfillmentMode(FulfillmentMode fulfillmentMode) {
        this.fulfillmentMode = fulfillmentMode;
    }

    public LocalDate getPickupDate() {
        return pickupDate;
    }

    public void setPickupDate(LocalDate pickupDate) {
        this.pickupDate = pickupDate;
    }

    public LocalTime getTimeSlotStart() {
        return timeSlotStart;
    }

    public void setTimeSlotStart(LocalTime timeSlotStart) {
        this.timeSlotStart = timeSlotStart;
    }

    public LocalTime getTimeSlotEnd() {
        return timeSlotEnd;
    }

    public void setTimeSlotEnd(LocalTime timeSlotEnd) {
        this.timeSlotEnd = timeSlotEnd;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(Long remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
