package com.easyeat.order.dto;

import com.easyeat.order.domain.FulfillmentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class CreateOrderRequest {

    @NotNull(message = "El establecimiento es obligatorio")
    private UUID establishmentId;

    @NotNull(message = "La modalidad de retiro es obligatoria")
    private FulfillmentMode fulfillmentMode;

    private LocalDate pickupDate;

    private LocalTime timeSlotStart;

    private LocalTime timeSlotEnd;

    @NotEmpty(message = "La orden debe contener al menos un plato")
    @Valid
    private List<CreateOrderItemRequest> items;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(UUID establishmentId,
                              FulfillmentMode fulfillmentMode,
                              LocalDate pickupDate,
                              LocalTime timeSlotStart,
                              LocalTime timeSlotEnd,
                              List<CreateOrderItemRequest> items) {
        this.establishmentId = establishmentId;
        this.fulfillmentMode = fulfillmentMode;
        this.pickupDate = pickupDate;
        this.timeSlotStart = timeSlotStart;
        this.timeSlotEnd = timeSlotEnd;
        this.items = items;
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

    public List<CreateOrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<CreateOrderItemRequest> items) {
        this.items = items;
    }
}
