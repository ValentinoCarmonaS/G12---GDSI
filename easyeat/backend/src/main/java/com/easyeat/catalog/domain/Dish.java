package com.easyeat.catalog.domain;

import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "dishes")
public class Dish {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "category", length = 50)
    private DishCategory category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = true, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DishStatus status = DishStatus.DRAFT;

    @Column(name = "allergens_confirmed", nullable = false)
    private Boolean allergensConfirmed = false;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /**
     * The retail price (PVP) assigned by the Administrator, owned by spec 007.
     *
     * <p>Nullable, and that is the point: {@code null} means "not yet priced", a state the
     * pre-existing {@code price} column cannot express. {@code price} is deprecated and survives
     * only for spec 003's artifacts.
     */
    @Column(name = "retail_price", precision = 10, scale = 2)
    private BigDecimal retailPrice;

    /**
     * The instant of the {@code PENDING_PRICE -> PUBLISHED} transition, owned by spec 007.
     * {@code null} for any dish never approved, and never cleared here — unpublishing belongs to
     * the Sprint 3 catalog feature.
     */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dish_dietary_restrictions", joinColumns = @JoinColumn(name = "dish_id"))
    @Column(name = "restriction_code")
    private Set<String> restrictionCodes = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Dish() {
    }

    public Dish(UUID tenantId, String name, String description, BigDecimal price, Boolean isActive) {
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.isActive = isActive;
        this.status = DishStatus.DRAFT;
        this.allergensConfirmed = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Dish(UUID tenantId, String name, DishCategory category, String description, BigDecimal price, Boolean isActive, UUID createdBy) {
        this.tenantId = tenantId;
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
        this.isActive = isActive != null ? isActive : false;
        this.createdBy = createdBy;
        this.status = DishStatus.DRAFT;
        this.allergensConfirmed = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DishCategory getCategory() {
        return category;
    }

    public void setCategory(DishCategory category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public DishStatus getStatus() {
        return status;
    }

    public void setStatus(DishStatus status) {
        this.status = status;
    }

    public Boolean getAllergensConfirmed() {
        return allergensConfirmed;
    }

    public Boolean isAllergensConfirmed() {
        return allergensConfirmed;
    }

    public void setAllergensConfirmed(Boolean allergensConfirmed) {
        this.allergensConfirmed = allergensConfirmed;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public BigDecimal getRetailPrice() {
        return retailPrice;
    }

    public void setRetailPrice(BigDecimal retailPrice) {
        this.retailPrice = retailPrice;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Set<String> getRestrictionCodes() {
        return restrictionCodes;
    }

    public void setRestrictionCodes(Set<String> restrictionCodes) {
        this.restrictionCodes = restrictionCodes;
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
}
