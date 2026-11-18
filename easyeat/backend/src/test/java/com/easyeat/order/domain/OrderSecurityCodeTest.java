package com.easyeat.order.domain;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 022, T004: write-once security code and delivery completion timestamp on {@link Order}
 * (research D-02, data-model invariants 1, 2 and 4; RF-001, RF-002, RF-011).
 */
@DisplayName("OrderSecurityCodeTest (T004, spec 022, research D-02)")
class OrderSecurityCodeTest {

    @Nested
    @DisplayName("assignSecurityCodeIfAbsent")
    class AssignSecurityCodeIfAbsent {

        @Test
        @DisplayName("assigns the code when the order has none (legacy order confirmed later, RF-001)")
        void assignsWhenAbsent() {
            Order order = new Order();

            order.assignSecurityCodeIfAbsent("4821");

            assertThat(order.getSecurityCode()).isEqualTo("4821");
        }

        @Test
        @DisplayName("keeps an identical code without failing (idempotent re-confirmation)")
        void keepsIdenticalCode() {
            Order order = new Order();
            order.assignSecurityCodeIfAbsent("4821");

            assertThatCode(() -> order.assignSecurityCodeIfAbsent("4821")).doesNotThrowAnyException();
            assertThat(order.getSecurityCode()).isEqualTo("4821");
        }

        @Test
        @DisplayName("throws IllegalStateException when replacing an assigned code and keeps the original (invariant 2)")
        void rejectsReplacement() {
            Order order = new Order();
            order.assignSecurityCodeIfAbsent("4821");

            assertThatThrownBy(() -> order.assignSecurityCodeIfAbsent("9102"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(order.getSecurityCode()).isEqualTo("4821");
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "0999", "10000", "999", "abcd", "12a4", " 4821"})
        @DisplayName("throws IllegalArgumentException for a code outside ^[1-9][0-9]{3}$ and leaves the order untouched")
        void rejectsMalformedCode(String malformed) {
            Order order = new Order();

            assertThatThrownBy(() -> order.assignSecurityCodeIfAbsent(malformed))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(order.getSecurityCode()).isNull();
        }

        @Test
        @DisplayName("validates the argument before the write-once check, even when a code is already assigned")
        void rejectsMalformedCodeOnAssignedOrder() {
            Order order = new Order();
            order.assignSecurityCodeIfAbsent("4821");

            assertThatThrownBy(() -> order.assignSecurityCodeIfAbsent("0999"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(order.getSecurityCode()).isEqualTo("4821");
        }

        @Test
        @DisplayName("accepts both range boundaries 1000 and 9999")
        void acceptsBoundaries() {
            Order low = new Order();
            Order high = new Order();

            low.assignSecurityCodeIfAbsent("1000");
            high.assignSecurityCodeIfAbsent("9999");

            assertThat(low.getSecurityCode()).isEqualTo("1000");
            assertThat(high.getSecurityCode()).isEqualTo("9999");
        }

        @Test
        @DisplayName("exposes no public setter that could bypass the write-once rule")
        void exposesNoPublicSetter() {
            assertThat(publicMethodNamed("setSecurityCode")).isFalse();
        }
    }

    @Nested
    @DisplayName("@PrePersist generation")
    class PrePersistGeneration {

        @Test
        @DisplayName("generates a code in [1000, 9999] when the order has none")
        void generatesWhenAbsent() {
            for (int i = 0; i < 200; i++) {
                Order order = new Order();

                order.onCreate();

                assertThat(order.getSecurityCode()).matches("^[1-9][0-9]{3}$");
                assertThat(Integer.parseInt(order.getSecurityCode())).isBetween(1000, 9999);
            }
        }

        @Test
        @DisplayName("preserves a code assigned before persisting")
        void preservesAssignedCode() {
            Order order = new Order();
            order.assignSecurityCodeIfAbsent("4821");

            order.onCreate();

            assertThat(order.getSecurityCode()).isEqualTo("4821");
        }
    }

    @Nested
    @DisplayName("completedAt")
    class CompletedAt {

        @Test
        @DisplayName("is null on a new order")
        void isNullByDefault() {
            assertThat(new Order().getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("maps the nullable orders.completed_at column added by V28")
        void mapsCompletedAtColumn() throws NoSuchFieldException {
            Field field = Order.class.getDeclaredField("completedAt");
            Column column = field.getAnnotation(Column.class);

            assertThat(field.getType()).isEqualTo(java.time.Instant.class);
            assertThat(column).isNotNull();
            assertThat(column.name()).isEqualTo("completed_at");
            assertThat(column.nullable()).isTrue();
        }

        @Test
        @DisplayName("exposes no public setter: only the delivery transition (T024) may fix it (invariant 4)")
        void exposesNoPublicSetter() {
            assertThat(publicMethodNamed("setCompletedAt")).isFalse();
        }
    }

    private static boolean publicMethodNamed(String name) {
        Method[] methods = Order.class.getDeclaredMethods();
        return Arrays.stream(methods)
                .anyMatch(m -> m.getName().equals(name) && Modifier.isPublic(m.getModifiers()));
    }
}
