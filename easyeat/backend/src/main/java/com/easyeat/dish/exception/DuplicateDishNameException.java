package com.easyeat.dish.exception;

import java.util.UUID;

public class DuplicateDishNameException extends RuntimeException {

    private final String dishName;
    private final UUID tenantId;

    public DuplicateDishNameException(String dishName) {
        super(String.format("Ya existe un plato registrado con el nombre '%s' en este establecimiento.", dishName));
        this.dishName = dishName;
        this.tenantId = null;
    }

    public DuplicateDishNameException(String dishName, UUID tenantId) {
        super(String.format("Ya existe un plato registrado con el nombre '%s' en el establecimiento %s.", dishName, tenantId));
        this.dishName = dishName;
        this.tenantId = tenantId;
    }

    public String getDishName() {
        return dishName;
    }

    public UUID getTenantId() {
        return tenantId;
    }
}
