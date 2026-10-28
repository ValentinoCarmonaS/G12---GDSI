package com.easyeat.catalog.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DishCatalogItemDto {

    private UUID id;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private String photoUrl;
    private Boolean isAvailable;
    private Boolean isCompliant;
    private List<String> conflictingRestrictions = new ArrayList<>();
    private List<String> recipeIngredients = new ArrayList<>();

    public DishCatalogItemDto() {
    }

    public DishCatalogItemDto(
            UUID id,
            String name,
            String description,
            String category,
            BigDecimal price,
            String photoUrl,
            Boolean isAvailable,
            Boolean isCompliant,
            List<String> conflictingRestrictions,
            List<String> recipeIngredients) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.photoUrl = photoUrl;
        this.isAvailable = isAvailable;
        this.isCompliant = isCompliant;
        this.conflictingRestrictions = conflictingRestrictions != null ? conflictingRestrictions : new ArrayList<>();
        this.recipeIngredients = recipeIngredients != null ? recipeIngredients : new ArrayList<>();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public Boolean getIsAvailable() {
        return isAvailable;
    }

    public void setIsAvailable(Boolean available) {
        isAvailable = available;
    }

    public Boolean getIsCompliant() {
        return isCompliant;
    }

    public void setIsCompliant(Boolean compliant) {
        isCompliant = compliant;
    }

    public List<String> getConflictingRestrictions() {
        return conflictingRestrictions;
    }

    public void setConflictingRestrictions(List<String> conflictingRestrictions) {
        this.conflictingRestrictions = conflictingRestrictions != null ? conflictingRestrictions : new ArrayList<>();
    }

    public List<String> getRecipeIngredients() {
        return recipeIngredients;
    }

    public void setRecipeIngredients(List<String> recipeIngredients) {
        this.recipeIngredients = recipeIngredients != null ? recipeIngredients : new ArrayList<>();
    }
}

