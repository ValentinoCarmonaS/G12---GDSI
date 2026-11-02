package com.easyeat.operations.domain;

/**
 * Online payment methods the platform supports. Only a visibility flag is stored per
 * establishment for each of them: no credentials, tokens or gateway SDKs (A-005).
 */
public enum PaymentMethod {
    MERCADO_PAGO,
    STRIPE
}
