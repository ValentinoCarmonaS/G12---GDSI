package com.easyeat.catalog.dto;

import java.util.ArrayList;
import java.util.List;

public class CategoryGroupDto {

    private String category;
    private List<DishCatalogItemDto> dishes = new ArrayList<>();

    public CategoryGroupDto() {
    }

    public CategoryGroupDto(String category, List<DishCatalogItemDto> dishes) {
        this.category = category;
        this.dishes = dishes != null ? dishes : new ArrayList<>();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<DishCatalogItemDto> getDishes() {
        return dishes;
    }

    public void setDishes(List<DishCatalogItemDto> dishes) {
        this.dishes = dishes != null ? dishes : new ArrayList<>();
    }
}

