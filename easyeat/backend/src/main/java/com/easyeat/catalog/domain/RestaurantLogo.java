package com.easyeat.catalog.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted binary logo image for a restaurant establishment (stored in database like UserPhoto and DishPhoto).
 */
@Entity
@Table(name = "restaurant_logos")
public class RestaurantLogo {

    @Id
    @Column(name = "establishment_id", nullable = false)
    private UUID establishmentId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Lob
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY)
    @Column(name = "data", nullable = false, columnDefinition = "BYTEA")
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RestaurantLogo() {
    }

    public RestaurantLogo(UUID establishmentId, String fileName, String contentType, Long fileSize, byte[] data) {
        this.establishmentId = establishmentId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.data = data;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public void setEstablishmentId(UUID establishmentId) {
        this.establishmentId = establishmentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
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
