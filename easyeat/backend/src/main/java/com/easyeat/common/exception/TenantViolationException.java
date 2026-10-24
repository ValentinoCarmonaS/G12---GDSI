package com.easyeat.common.exception;

import java.util.UUID;

public class TenantViolationException extends RuntimeException {

    private final UUID expectedTenantId;
    private final UUID actualTenantId;

    public TenantViolationException(String message) {
        super(message);
        this.expectedTenantId = null;
        this.actualTenantId = null;
    }

    public TenantViolationException(UUID expectedTenantId, UUID actualTenantId) {
        super(String.format("Cross-tenant access violation: resource belongs to tenant %s but active context is %s",
                expectedTenantId, actualTenantId));
        this.expectedTenantId = expectedTenantId;
        this.actualTenantId = actualTenantId;
    }

    public UUID getExpectedTenantId() {
        return expectedTenantId;
    }

    public UUID getActualTenantId() {
        return actualTenantId;
    }
}
