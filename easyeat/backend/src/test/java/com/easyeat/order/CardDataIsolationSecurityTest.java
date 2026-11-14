package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.RestaurantBranding;
import com.easyeat.catalog.domain.RestaurantLogo;
import com.easyeat.catalog.dto.RestaurantBrandingResponse;
import com.easyeat.catalog.dto.RestaurantBrandingUpdateRequest;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.entity.StockAuditEntry;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderReceipt;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T038: Automated security and privacy test auditing entities, DTOs, database migration schemas,
 * and application logs to certify zero storage, capture, or leakage of sensitive card data (PAN/CVV)
 * in full compliance with PCI-DSS SAQ A, INV-11 and SC-009.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("T038: CardDataIsolationSecurityTest (PCI-DSS SAQ A, INV-11, SC-009)")
class CardDataIsolationSecurityTest {

    private static final List<String> FORBIDDEN_CARD_PATTERNS = List.of(
            "pan", "cvv", "cvc", "securitycode", "expirationmonth", "expirationyear",
            "cardholder", "creditcard", "debitcard", "tarjeta", "titular", "cardnumber"
    );

    private static final Pattern CARD_NUMBER_REGEX = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\\b");
    private static final Pattern CVV_REGEX = Pattern.compile("(?i)\\b(?:cvv|cvc|security_code)\\s*[:=]\\s*[0-9]{3,4}\\b");

    @Test
    @DisplayName("PCI-DSS SAQ A / INV-11: Entidades del dominio jamás contienen campos de datos de tarjeta")
    void domainEntitiesMustNotContainSensitiveCardFields() {
        List<Class<?>> domainClasses = List.of(
                Payment.class,
                Order.class,
                OrderItem.class,
                TemporaryReservation.class,
                KdsTicket.class,
                OrderReceipt.class,
                Ingredient.class,
                StockAuditEntry.class,
                Dish.class,
                DishRecipeIngredient.class,
                Establishment.class,
                User.class,
                RestaurantBranding.class,
                RestaurantLogo.class
        );

        for (Class<?> clazz : domainClasses) {
            assertNoForbiddenCardFields(clazz);
        }
    }

    @Test
    @DisplayName("PCI-DSS SAQ A / INV-11: DTOs de checkout, tracking y pasarela jamás capturan ni exponen datos de tarjeta")
    void dtosMustNotContainSensitiveCardFields() {
        List<Class<?>> dtoClasses = List.of(
                PaymentInitiationRequest.class,
                PaymentInitiationResponse.class,
                PaymentConfirmationRequest.class,
                PaymentConfirmationResponse.class,
                PendingPaymentRequest.class,
                PendingPaymentResponse.class,
                ZeroAmountOrderRequest.class,
                ZeroAmountOrderResponse.class,
                KdsTicketSummaryDto.class,
                OrderConfirmationTrackingResponse.class,
                OrderLiveTrackingResponse.class,
                RestaurantBrandingResponse.class,
                RestaurantBrandingUpdateRequest.class
        );

        for (Class<?> clazz : dtoClasses) {
            assertNoForbiddenCardFields(clazz);
        }
    }

    @Test
    @DisplayName("PCI-DSS SAQ A: Esquemas Flyway SQL no poseen columnas para almacenar PAN o CVV")
    void flywayMigrationSchemasMustNotContainCardDataColumns() throws IOException {
        Path migrationsDir = Path.of("src/main/resources/db/migration");
        if (!Files.exists(migrationsDir)) {
            migrationsDir = Path.of("easyeat/backend/src/main/resources/db/migration");
        }

        assertThat(Files.exists(migrationsDir))
                .as("El directorio de migraciones Flyway debe existir")
                .isTrue();

        try (Stream<Path> files = Files.walk(migrationsDir)) {
            List<Path> sqlFiles = files.filter(p -> p.toString().endsWith(".sql")).toList();
            assertThat(sqlFiles).isNotEmpty();

            for (Path sqlFile : sqlFiles) {
                String content = Files.readString(sqlFile).toLowerCase();
                for (String forbidden : List.of("card_number", "cardnumber", "pan", "cvv", "cvc", "security_code", "cardholder_name")) {
                    assertThat(content)
                            .as("El script de migración %s no debe definir columnas de tarjetas (%s)", sqlFile.getFileName(), forbidden)
                            .doesNotContain(forbidden + " ");
                }
            }
        }
    }

    @Test
    @DisplayName("PCI-DSS SAQ A / Logs: Salida estándar y logs nunca registran números de tarjeta (PAN) ni CVV")
    void logsAndConsoleOutputMustNotLeakCreditCardNumbersOrCvv(CapturedOutput output) {
        String logText = output.getAll();
        assertThat(CARD_NUMBER_REGEX.matcher(logText).find())
                .as("Los logs de la aplicación no deben contener números de tarjeta de crédito (PAN) coincidentes con algoritmos de emisión")
                .isFalse();

        assertThat(CVV_REGEX.matcher(logText).find())
                .as("Los logs de la aplicación no deben contener códigos de seguridad (CVV/CVC)")
                .isFalse();
    }

    private void assertNoForbiddenCardFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        List<String> detectedCardFields = Arrays.stream(fields)
                .map(Field::getName)
                .filter(name -> FORBIDDEN_CARD_PATTERNS.stream().anyMatch(pattern -> name.toLowerCase().contains(pattern)))
                .collect(Collectors.toList());

        assertThat(detectedCardFields)
                .as("La clase %s no debe contener ningún campo de datos de tarjeta bancaria (PCI-DSS SAQ A, INV-11)", clazz.getSimpleName())
                .isEmpty();
    }
}
