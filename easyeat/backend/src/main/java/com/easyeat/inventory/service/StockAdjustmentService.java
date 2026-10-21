package com.easyeat.inventory.service;

import com.easyeat.inventory.dto.StockAdjustmentRequest;
import com.easyeat.inventory.dto.StockAdjustmentResponse;
import com.easyeat.inventory.dto.StockAuditEntryResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class StockAdjustmentService {

    public StockAdjustmentResponse adjustStock(UUID ingredientId, StockAdjustmentRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<StockAuditEntryResponse> getAuditHistory(UUID ingredientId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
