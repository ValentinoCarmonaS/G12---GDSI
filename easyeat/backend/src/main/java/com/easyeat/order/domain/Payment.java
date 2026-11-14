package com.easyeat.order.domain;

import com.easyeat.operations.domain.PaymentMethod;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private TemporaryReservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod = PaymentMethod.MERCADO_PAGO;

    @Column(name = "gateway_preference_id", length = 100)
    private String gatewayPreferenceId;

    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    @Column(name = "external_reference", nullable = false, length = 100)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.IN_PROCESS;

    @Column(name = "status_detail", length = 100)
    private String statusDetail;

    @Column(name = "transaction_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal transactionAmount;

    @Column(name = "init_point", length = 500)
    private String initPoint;

    @Column(name = "sandbox_init_point", length = 500)
    private String sandboxInitPoint;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    protected Payment() {
    }

    public Payment(Order order,
                   TemporaryReservation reservation,
                   PaymentMethod paymentMethod,
                   String externalReference,
                   BigDecimal transactionAmount,
                   String idempotencyKey) {
        this.order = order;
        this.reservation = reservation;
        this.paymentMethod = paymentMethod != null ? paymentMethod : PaymentMethod.MERCADO_PAGO;
        this.externalReference = externalReference;
        this.transactionAmount = transactionAmount;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.IN_PROCESS;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void assignGatewayPreference(String gatewayPreferenceId, String initPoint, String sandboxInitPoint) {
        this.gatewayPreferenceId = gatewayPreferenceId;
        this.initPoint = initPoint;
        this.sandboxInitPoint = sandboxInitPoint;
        this.updatedAt = Instant.now();
    }

    public void approve(String gatewayPaymentId, String statusDetail) {
        this.status = PaymentStatus.APPROVED;
        this.gatewayPaymentId = gatewayPaymentId;
        this.statusDetail = statusDetail;
        this.approvedAt = Instant.now();
        this.updatedAt = this.approvedAt;
    }

    public void reject(String gatewayPaymentId, String statusDetail) {
        this.status = PaymentStatus.REJECTED;
        this.gatewayPaymentId = gatewayPaymentId;
        this.statusDetail = statusDetail;
        this.rejectedAt = Instant.now();
        this.updatedAt = this.rejectedAt;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void fail(String statusDetail) {
        this.status = PaymentStatus.FAILED;
        this.statusDetail = statusDetail;
        this.updatedAt = Instant.now();
    }

    public void expire() {
        this.status = PaymentStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public TemporaryReservation getReservation() {
        return reservation;
    }

    public void setReservation(TemporaryReservation reservation) {
        this.reservation = reservation;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getGatewayPreferenceId() {
        return gatewayPreferenceId;
    }

    public void setGatewayPreferenceId(String gatewayPreferenceId) {
        this.gatewayPreferenceId = gatewayPreferenceId;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public void setGatewayPaymentId(String gatewayPaymentId) {
        this.gatewayPaymentId = gatewayPaymentId;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public void setStatusDetail(String statusDetail) {
        this.statusDetail = statusDetail;
    }

    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(BigDecimal transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public String getInitPoint() {
        return initPoint;
    }

    public void setInitPoint(String initPoint) {
        this.initPoint = initPoint;
    }

    public String getSandboxInitPoint() {
        return sandboxInitPoint;
    }

    public void setSandboxInitPoint(String sandboxInitPoint) {
        this.sandboxInitPoint = sandboxInitPoint;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }
}

