package com.easyeat.dish.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DishCategoryConverter implements AttributeConverter<DishCategory, String> {

    @Override
    public String convertToDatabaseColumn(DishCategory attribute) {
        return attribute != null ? attribute.getValue() : null;
    }

    @Override
    public DishCategory convertToEntityAttribute(String dbData) {
        return dbData != null ? DishCategory.fromValue(dbData) : null;
    }
}
