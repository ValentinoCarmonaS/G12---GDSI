package com.easyeat.inventory.service;

import com.easyeat.inventory.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class IngredientService {

    public AdminIngredientDetailResponse createIngredient(CreateIngredientRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AdminIngredientDetailResponse getIngredient(UUID id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<AdminIngredientDetailResponse> listIngredients(IngredientCategory category, IngredientStatus status) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AdminIngredientDetailResponse updateIngredient(UUID id, UpdateIngredientRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AdminIngredientDetailResponse updateIngredientStatus(UUID id, IngredientStatusUpdateRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deleteIngredient(UUID id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
