package com.easyeat.inventory.entity;

import com.easyeat.inventory.dto.IngredientCategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IngredientCategoryConverter implements AttributeConverter<IngredientCategory, String> {

    @Override
    public String convertToDatabaseColumn(IngredientCategory attribute) {
        return attribute != null ? attribute.getValue() : null;
    }

    @Override
    public IngredientCategory convertToEntityAttribute(String dbData) {
        return dbData != null ? IngredientCategory.fromValue(dbData) : null;
    }
}
