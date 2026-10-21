package com.easyeat.inventory.entity;

import com.easyeat.inventory.dto.UnitOfMeasure;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UnitOfMeasureConverter implements AttributeConverter<UnitOfMeasure, String> {

    @Override
    public String convertToDatabaseColumn(UnitOfMeasure attribute) {
        return attribute != null ? attribute.getValue() : null;
    }

    @Override
    public UnitOfMeasure convertToEntityAttribute(String dbData) {
        return dbData != null ? UnitOfMeasure.fromValue(dbData) : null;
    }
}
