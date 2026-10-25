package com.easyeat.profile.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "dietary_restrictions")
public class DietaryRestriction {

    @Id
    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50, nullable = false)
    private DietaryCategory category;

    @Column(name = "description", length = 255)
    private String description;

    public DietaryRestriction() {
    }

    public DietaryRestriction(String code, String name, DietaryCategory category, String description) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DietaryCategory getCategory() {
        return category;
    }

    public void setCategory(DietaryCategory category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
