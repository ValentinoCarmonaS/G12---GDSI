package com.easyeat.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "orders")
public class Order {

    /** Same rule as {@code chk_orders_security_code}: four digits in [1000, 9999]. */
    private static final Pattern SECURITY_CODE_FORMAT = Pattern.compile("^[1-9][0-9]{3}$");

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "establishment_id", nullable = false)
    private UUID establishmentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_mode", nullable = false, length = 30)
    private FulfillmentMode fulfillmentMode;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "time_slot_start", nullable = false)
    private LocalTime timeSlotStart;

    @Column(name = "time_slot_end", nullable = false)
    private LocalTime timeSlotEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "security_code", length = 4)
    private String securityCode;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Order(UUID id, UUID establishmentId, UUID userId, FulfillmentMode fulfillmentMode, LocalDate pickupDate,
                 LocalTime timeSlotStart, LocalTime timeSlotEnd, OrderStatus status, BigDecimal totalAmount,
                 Instant expiresAt) {
        this.id = id;
        this.establishmentId = establishmentId;
        this.userId = userId;
        this.fulfillmentMode = fulfillmentMode;
        this.pickupDate = pickupDate;
        this.timeSlotStart = timeSlotStart;
        this.timeSlotEnd = timeSlotEnd;
        this.status = status != null ? status : OrderStatus.PENDING_PAYMENT;
        this.totalAmount = totalAmount;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (expiresAt == null && status == OrderStatus.PENDING_PAYMENT) {
            expiresAt = createdAt.plus(15, ChronoUnit.MINUTES);
        }
        if (!hasSecurityCode()) {
            assignSecurityCodeIfAbsent(com.easyeat.order.service.OrderService.generateSecurityCode());
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        if (item != null) {
            items.add(item);
            item.setOrder(this);
        }
    }

    public void removeItem(OrderItem item) {
        if (item != null) {
            items.remove(item);
            item.setOrder(null);
        }
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now != null && !expiresAt.isAfter(now);
    }

    public boolean isPendingAccreditation() {
        return status == OrderStatus.PENDING_ACCREDITATION;
    }

    public boolean isInProgress() {
        return status == OrderStatus.IN_PROGRESS;
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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public Instant getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(Instant readyAt) {
        this.readyAt = readyAt;
    }

    public String getSecurityCode() {
        return securityCode;
    }

    /**
     * Write-once assignment of the pickup PIN (research D-02, data-model invariant 2): assigns the code
     * when the order has none, keeps an identical one, and refuses to replace a different one.
     *
     * @throws IllegalArgumentException if {@code code} is null, blank or outside [1000, 9999]
     * @throws IllegalStateException    if the order already holds a different code
     */
    public void assignSecurityCodeIfAbsent(String code) {
        if (code == null || !SECURITY_CODE_FORMAT.matcher(code).matches()) {
            throw new IllegalArgumentException("Security code must be four digits in [1000, 9999]");
        }
        if (!hasSecurityCode()) {
            this.securityCode = code;
        } else if (!securityCode.equals(code)) {
            throw new IllegalStateException("Order " + id + " already has a security code; it cannot be replaced");
        }
    }

    private boolean hasSecurityCode() {
        return securityCode != null && !securityCode.isBlank();
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    /**
     * Terminal delivery transition (spec 022, data-model invariants 4 and 5): a READY_FOR_PICKUP order becomes
     * COMPLETED and its completion instant is fixed once and for all.
     *
     * @throws IllegalArgumentException if {@code deliveredAt} is null
     * @throws IllegalStateException    if the order is not READY_FOR_PICKUP or was already delivered
     */
    public void markDelivered(Instant deliveredAt) {
        if (deliveredAt == null) {
            throw new IllegalArgumentException("The delivery instant is required");
        }
        if (status != OrderStatus.READY_FOR_PICKUP || completedAt != null) {
            throw new IllegalStateException("Order " + id + " cannot be delivered from status " + status);
        }
        this.status = OrderStatus.COMPLETED;
        this.completedAt = deliveredAt;
        this.updatedAt = deliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}
