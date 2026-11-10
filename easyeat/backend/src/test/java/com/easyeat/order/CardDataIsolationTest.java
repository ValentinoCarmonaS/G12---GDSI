package com.easyeat.order;

import com.easyeat.catalog.domain.RestaurantBranding;
import com.easyeat.catalog.domain.RestaurantLogo;
import com.easyeat.catalog.dto.RestaurantBrandingResponse;
import com.easyeat.catalog.dto.RestaurantBrandingUpdateRequest;
import com.easyeat.order.domain.OrderReceipt;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.dto.OrderConfirmationTrackingResponse;
import com.easyeat.order.dto.OrderLiveTrackingResponse;
import com.easyeat.order.dto.PaymentInitiationRequest;
import com.easyeat.order.dto.PaymentInitiationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T020 / T054: CardDataIsolationTest (FR-013, INV-11, SC-009, PCI-DSS SAQ A)")
class CardDataIsolationTest {

    private final List<String> forbiddenCardPatterns = List.of(
            "card", "pan", "cvv", "cvc", "securitycode", "expirationmonth", "expirationyear",
            "cardholder", "creditcard", "debitcard", "tarjeta", "titular"
    );

    @Test
    @DisplayName("INV-11: La entidad Payment no contiene campos relacionados con tarjetas de crédito o débito")
    void paymentEntityMustNotContainCardFields() {
        assertNoForbiddenFields(Payment.class);
    }

    @Test
    @DisplayName("INV-11: La entidad TemporaryReservation no contiene campos relacionados con tarjetas de crédito o débito")
    void temporaryReservationEntityMustNotContainCardFields() {
        assertNoForbiddenFields(TemporaryReservation.class);
    }

    @Test
    @DisplayName("INV-11: El DTO PaymentInitiationRequest no contiene campos para captura de tarjetas")
    void paymentInitiationRequestDtoMustNotContainCardFields() {
        assertNoForbiddenFields(PaymentInitiationRequest.class);
    }

    @Test
    @DisplayName("INV-11: El DTO PaymentInitiationResponse no contiene ni expone campos de tarjetas")
    void paymentInitiationResponseDtoMustNotContainCardFields() {
        assertNoForbiddenFields(PaymentInitiationResponse.class);
    }

    @Test
    @DisplayName("T054 (PCI-DSS SAQ A): La entidad OrderReceipt no almacena datos de tarjeta de crédito o débito")
    void orderReceiptMustNotContainCardFields() {
        assertNoForbiddenFields(OrderReceipt.class);
    }

    @Test
    @DisplayName("T054 (PCI-DSS SAQ A): DTOs de confirmación y live tracking no exponen datos de tarjeta")
    void orderTrackingDtosMustNotContainCardFields() {
        assertNoForbiddenFields(OrderConfirmationTrackingResponse.class);
        assertNoForbiddenFields(OrderLiveTrackingResponse.class);
    }

    @Test
    @DisplayName("T054 (PCI-DSS SAQ A): Entidades y DTOs de branding multi-tenant no contienen datos de tarjeta")
    void brandingEntitiesAndDtosMustNotContainCardFields() {
        assertNoForbiddenFields(RestaurantBranding.class);
        assertNoForbiddenFields(RestaurantLogo.class);
        assertNoForbiddenFields(RestaurantBrandingResponse.class);
        assertNoForbiddenFields(RestaurantBrandingUpdateRequest.class);
    }

    private void assertNoForbiddenFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        List<String> detectedCardFields = Arrays.stream(fields)
                .map(Field::getName)
                .filter(name -> forbiddenCardPatterns.stream().anyMatch(pattern -> name.toLowerCase().contains(pattern)))
                .collect(Collectors.toList());

        assertThat(detectedCardFields)
                .as("La clase %s no debe contener ningún campo de datos de tarjeta bancaria (PCI-DSS SAQ A, INV-11)", clazz.getSimpleName())
                .isEmpty();
    }
}
