package com.easyeat.dish.dto;

import java.util.List;
import java.util.UUID;

public record AllergenResponse(
        UUID dishId,
        List<String> restrictionCodes,
        boolean allergensConfirmed
) {
}
