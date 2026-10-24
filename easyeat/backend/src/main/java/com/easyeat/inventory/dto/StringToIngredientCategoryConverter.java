package com.easyeat.inventory.dto;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToIngredientCategoryConverter implements Converter<String, IngredientCategory> {

    @Override
    public IngredientCategory convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return IngredientCategory.fromValue(source.trim());
    }
}
