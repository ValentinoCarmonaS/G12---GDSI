package com.easyeat.order.domain;

import com.easyeat.catalog.domain.Establishment;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Entidad relacional para comandas despachadas a la cocina (Kitchen Display System).
 */
@Entity
@Table(
        name = "kds_tickets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_kds_tickets_order_id", columnNames = "order_id")
        },
        indexes = {
                @Index(name = "idx_kds_tickets_establishment_status", columnList = "establishment_id, status, pickup_date, time_slot_start"),
                @Index(name = "idx_kds_tickets_order_id", columnList = "order_id")
        }
)
public class KdsTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    private Establishment establishment;

    @Column(name = "ticket_number", nullable = false)
    private Integer ticketNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private KdsTicketStatus status = KdsTicketStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_mode", nullable = false, length = 30)
    private FulfillmentMode fulfillmentMode;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "time_slot_start", nullable = false)
    private LocalTime timeSlotStart;

    @Column(name = "time_slot_end", nullable = false)
    private LocalTime timeSlotEnd;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public KdsTicket() {
    }

    public KdsTicket(UUID id, UUID orderId, Establishment establishment, Integer ticketNumber,
                     KdsTicketStatus status, FulfillmentMode fulfillmentMode, LocalDate pickupDate,
                     LocalTime timeSlotStart, LocalTime timeSlotEnd, Instant queuedAt) {
        this.id = id;
        this.orderId = orderId;
        this.establishment = establishment;
        this.ticketNumber = ticketNumber;
        this.status = status != null ? status : KdsTicketStatus.QUEUED;
        this.fulfillmentMode = fulfillmentMode;
        this.pickupDate = pickupDate;
        this.timeSlotStart = timeSlotStart;
        this.timeSlotEnd = timeSlotEnd;
        this.queuedAt = queuedAt != null ? queuedAt : Instant.now();
    }

    @PrePersist
    public void onCreate() {
        if (this.queuedAt == null) {
            this.queuedAt = Instant.now();
        }
        if (this.status == null) {
            this.status = KdsTicketStatus.QUEUED;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public Establishment getEstablishment() {
        return establishment;
    }

    public void setEstablishment(Establishment establishment) {
        this.establishment = establishment;
    }

    public Integer getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(Integer ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    public KdsTicketStatus getStatus() {
        return status;
    }

    public void setStatus(KdsTicketStatus status) {
        this.status = status;
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

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(Instant queuedAt) {
        this.queuedAt = queuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(Instant readyAt) {
        this.readyAt = readyAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
