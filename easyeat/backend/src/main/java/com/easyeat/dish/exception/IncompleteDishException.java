package com.easyeat.dish.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class IncompleteDishException extends RuntimeException {

    private final UUID dishId;
    private final List<String> missingConditions;

    public IncompleteDishException(UUID dishId, List<String> missingConditions) {
        super("El plato no cumple con todas las condiciones requeridas para ser enviado a revisión.");
        this.dishId = dishId;
        this.missingConditions = missingConditions != null ? Collections.unmodifiableList(new ArrayList<>(missingConditions)) : List.of();
    }

    public IncompleteDishException(String message, List<String> missingConditions) {
        super(message);
        this.dishId = null;
        this.missingConditions = missingConditions != null ? Collections.unmodifiableList(new ArrayList<>(missingConditions)) : List.of();
    }

    public UUID getDishId() {
        return dishId;
    }

    public List<String> getMissingConditions() {
        return missingConditions;
    }
}
