package com.easyeat.operations.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Online payment methods the platform supports. Only a visibility flag is stored per
 * establishment for each of them: no credentials, tokens or gateway SDKs (A-005).
 */
public enum PaymentMethod {
    MERCADO_PAGO("Mercado Pago"),
    STRIPE("Stripe");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Resolves a webhook {@code {provider}} path slug uniformly without exception-based control flow,
     * ignoring case and {@code -}/{@code _} separators (for example, both {@code mercadopago} and
     * {@code mercado-pago} resolve to {@link #MERCADO_PAGO}).
     */
    public static Optional<PaymentMethod> fromWebhookSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeSlug(slug);
        return Arrays.stream(values())
                .filter(method -> normalizeSlug(method.name()).equals(normalized))
                .findFirst();
    }

    private static String normalizeSlug(String raw) {
        return raw.replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
    }
}
