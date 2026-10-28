package com.easyeat.dish.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DishDomainExceptionsTest {

    @Test
    @DisplayName("DishNotFoundException should retain dishId, message, and inherit from catalog exception")
    void dishNotFoundException() {
        UUID dishId = UUID.randomUUID();
        DishNotFoundException exWithId = new DishNotFoundException(dishId);
        assertThat(exWithId.getDishId()).isEqualTo(dishId);
        assertThat(exWithId.getMessage()).contains(dishId.toString());
        assertThat(exWithId).isInstanceOf(com.easyeat.catalog.exception.DishNotFoundException.class);

        DishNotFoundException exWithMessage = new DishNotFoundException("Plato no encontrado");
        assertThat(exWithMessage.getMessage()).isEqualTo("Plato no encontrado");
    }

    @Test
    @DisplayName("DishLockedException should retain dishId and currentStatus")
    void dishLockedException() {
        UUID dishId = UUID.randomUUID();
        DishLockedException ex = new DishLockedException(dishId, "PENDING_PRICE");
        assertThat(ex.getDishId()).isEqualTo(dishId);
        assertThat(ex.getCurrentStatus()).isEqualTo("PENDING_PRICE");
        assertThat(ex.getMessage()).contains("PENDING_PRICE");
        assertThat(ex.getMessage()).contains(dishId.toString());

        DishLockedException exCustom = new DishLockedException("Plato bloqueado");
        assertThat(exCustom.getMessage()).isEqualTo("Plato bloqueado");
    }

    @Test
    @DisplayName("DuplicateDishNameException should retain dishName and tenantId")
    void duplicateDishNameException() {
        UUID tenantId = UUID.randomUUID();
        DuplicateDishNameException ex = new DuplicateDishNameException("Hamburguesa Clásica", tenantId);
        assertThat(ex.getDishName()).isEqualTo("Hamburguesa Clásica");
        assertThat(ex.getTenantId()).isEqualTo(tenantId);
        assertThat(ex.getMessage()).contains("Hamburguesa Clásica");

        DuplicateDishNameException exSimple = new DuplicateDishNameException("Risotto");
        assertThat(exSimple.getDishName()).isEqualTo("Risotto");
        assertThat(exSimple.getMessage()).contains("Risotto");
    }

    @Test
    @DisplayName("IncompleteDishException should retain dishId and missingConditions list")
    void incompleteDishException() {
        UUID dishId = UUID.randomUUID();
        List<String> conditions = List.of(
                "La receta debe contener al menos un ingrediente",
                "Se debe confirmar la sección de alérgenos"
        );
        IncompleteDishException ex = new IncompleteDishException(dishId, conditions);
        assertThat(ex.getDishId()).isEqualTo(dishId);
        assertThat(ex.getMissingConditions()).containsExactlyElementsOf(conditions);
        assertThat(ex.getMessage()).isNotEmpty();

        IncompleteDishException exWithMessage = new IncompleteDishException("Condiciones incompletas", conditions);
        assertThat(exWithMessage.getMessage()).isEqualTo("Condiciones incompletas");
        assertThat(exWithMessage.getMissingConditions()).hasSize(2);
    }

    @Test
    @DisplayName("InvalidIngredientException should retain ingredientId and reason")
    void invalidIngredientException() {
        UUID ingredientId = UUID.randomUUID();
        InvalidIngredientException ex = new InvalidIngredientException(ingredientId, "El ingrediente se encuentra DISCONTINUED");
        assertThat(ex.getIngredientId()).isEqualTo(ingredientId);
        assertThat(ex.getReason()).isEqualTo("El ingrediente se encuentra DISCONTINUED");
        assertThat(ex.getMessage()).contains(ingredientId.toString());

        InvalidIngredientException exSimple = new InvalidIngredientException("Ingrediente no válido");
        assertThat(exSimple.getMessage()).isEqualTo("Ingrediente no válido");
    }

    @Test
    @DisplayName("InvalidQuantityException should retain quantity, unitOfMeasure and reason")
    void invalidQuantityException() {
        BigDecimal qty = new BigDecimal("-2.5000");
        InvalidQuantityException ex = new InvalidQuantityException(qty, "kg", "La cantidad debe ser estrictamente positiva");
        assertThat(ex.getQuantity()).isEqualTo(qty);
        assertThat(ex.getUnitOfMeasure()).isEqualTo("kg");
        assertThat(ex.getReason()).isEqualTo("La cantidad debe ser estrictamente positiva");
        assertThat(ex.getMessage()).contains("-2.5000");

        InvalidQuantityException exSimple = new InvalidQuantityException("Cantidad inválida");
        assertThat(exSimple.getMessage()).isEqualTo("Cantidad inválida");
    }

    @Test
    @DisplayName("PhotoStorageException should support message and cause constructors")
    void photoStorageException() {
        PhotoStorageException ex = new PhotoStorageException("Formato no soportado");
        assertThat(ex.getMessage()).isEqualTo("Formato no soportado");

        Throwable cause = new IllegalArgumentException("Payload corrupto");
        PhotoStorageException exWithCause = new PhotoStorageException("Error al almacenar foto", cause);
        assertThat(exWithCause.getMessage()).isEqualTo("Error al almacenar foto");
        assertThat(exWithCause.getCause()).isEqualTo(cause);
    }
}
