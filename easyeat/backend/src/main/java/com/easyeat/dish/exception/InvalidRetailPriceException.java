package com.easyeat.dish.exception;

import java.math.BigDecimal;

/**
 * The submitted retail price is not a price this system can store (FR-013): it is zero or below,
 * or it carries more than two decimal places.
 *
 * <p>Thrown from the domain rather than left to bean validation, deliberately. FR-013 states 400,
 * while this codebase's bean-validation path yields 422, and the specification is the authority
 * (Principle I). Annotating the field would pre-empt this check and return the wrong status — the
 * trade-off is recorded in `plan.md` Complexity Tracking.
 *
 * <p>Maps to 400 {@code invalid-retail-price}.
 */
public class InvalidRetailPriceException extends RuntimeException {

    private static final int MAX_SCALE = 2;

    private final BigDecimal submittedPrice;

    public InvalidRetailPriceException(BigDecimal submittedPrice) {
        super(buildMessage(submittedPrice));
        this.submittedPrice = submittedPrice;
    }

    private static String buildMessage(BigDecimal price) {
        if (price == null) {
            return "El precio de venta es obligatorio.";
        }
        if (price.signum() <= 0) {
            return String.format(
                    "El precio de venta debe ser mayor que cero. Se recibió %s.",
                    price.toPlainString());
        }
        if (price.scale() > MAX_SCALE) {
            return String.format(
                    "El precio de venta admite como máximo %d decimales. Se recibió %s.",
                    MAX_SCALE, price.toPlainString());
        }
        return String.format("El precio de venta %s no es válido.", price.toPlainString());
    }

    public BigDecimal getSubmittedPrice() {
        return submittedPrice;
    }
}
