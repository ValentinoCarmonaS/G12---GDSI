package com.easyeat.dish.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.dish.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerDishTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/dishes/test");
    }

    @Test
    @DisplayName("Should map DishNotFoundException to 404 NOT_FOUND ProblemDetail")
    void handleDishNotFoundException() {
        UUID dishId = UUID.randomUUID();
        DishNotFoundException ex = new DishNotFoundException(dishId);
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleDishNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("https://easyeat.com/errors/dish-not-found"));
        assertThat(body.getTitle()).isEqualTo("Dish Not Found");
        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getDetail()).contains(dishId.toString());
        assertThat(body.getInstance()).isEqualTo(URI.create("/api/v1/dishes/test"));
    }

    @Test
    @DisplayName("Should map DishLockedException to 409 CONFLICT ProblemDetail")
    void handleDishLockedException() {
        UUID dishId = UUID.randomUUID();
        DishLockedException ex = new DishLockedException(dishId, "PENDING_PRICE");
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleDishLocked(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("https://easyeat.com/errors/dish-locked"));
        assertThat(body.getTitle()).isEqualTo("Dish Locked");
        assertThat(body.getStatus()).isEqualTo(409);
        assertThat(body.getDetail()).contains("PENDING_PRICE");
        assertThat(body.getProperties()).containsEntry("dishId", dishId.toString());
        assertThat(body.getProperties()).containsEntry("currentStatus", "PENDING_PRICE");
    }

    @Test
    @DisplayName("Should map DuplicateDishNameException to 409 CONFLICT ProblemDetail")
    void handleDuplicateDishNameException() {
        UUID tenantId = UUID.randomUUID();
        DuplicateDishNameException ex = new DuplicateDishNameException("Risotto de Hongos", tenantId);
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleDuplicateDishName(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("https://easyeat.com/errors/dish-name-duplicate"));
        assertThat(body.getTitle()).isEqualTo("Duplicate Dish Name");
        assertThat(body.getStatus()).isEqualTo(409);
        assertThat(body.getDetail()).contains("Risotto de Hongos");
        assertThat(body.getProperties()).containsEntry("dishName", "Risotto de Hongos");
        assertThat(body.getProperties()).containsEntry("tenantId", tenantId.toString());
    }

    @Test
    @DisplayName("Should map IncompleteDishException to 422 UNPROCESSABLE_ENTITY ProblemDetail")
    void handleIncompleteDishException() {
        UUID dishId = UUID.randomUUID();
        List<String> missing = List.of(
                "La receta debe contener al menos un ingrediente",
                "Se debe confirmar la sección de alérgenos"
        );
        IncompleteDishException ex = new IncompleteDishException(dishId, missing);
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleIncompleteDish(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("https://easyeat.com/errors/dish-incomplete"));
        assertThat(body.getTitle()).isEqualTo("Incomplete Dish Technical File");
        assertThat(body.getStatus()).isEqualTo(422);
        assertThat(body.getProperties()).containsEntry("dishId", dishId.toString());
        assertThat(body.getProperties()).containsEntry("missingConditions", missing);
    }

    @Test
    @DisplayName("Should map InvalidIngredientException to 400 BAD_REQUEST ProblemDetail")
    void handleInvalidIngredientException() {
        UUID ingredientId = UUID.randomUUID();
        InvalidIngredientException ex = new InvalidIngredientException(ingredientId, "DISCONTINUED");
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleInvalidIngredient(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("https://easyeat.com/errors/invalid-ingredient"));
        assertThat(body.getTitle()).isEqualTo("Invalid Recipe Ingredient");
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getProperties()).containsEntry("ingredientId", ingredientId.toString());
    }

    @Test
    @DisplayName("Should map InvalidQuantityException to 400 BAD_REQUEST ProblemDetail")
    void handleInvalidQuantityException() {
        InvalidQuantityException ex = new InvalidQuantityException(new BigDecimal("-1.5"), "kg", "Positive required");
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleInvalidQuantity(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("https://easyeat.com/errors/invalid-quantity"));
        assertThat(body.getTitle()).isEqualTo("Invalid Recipe Quantity");
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getProperties()).containsEntry("quantity", new BigDecimal("-1.5"));
        assertThat(body.getProperties()).containsEntry("unitOfMeasure", "kg");
    }

    @Test
    @DisplayName("Should map PhotoStorageException to 400 BAD_REQUEST ProblemDetail")
    void handlePhotoStorageException() {
        PhotoStorageException ex = new PhotoStorageException("MIME type no soportado");
        ResponseEntity<ProblemDetail> response = exceptionHandler.handlePhotoStorage(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("https://easyeat.com/errors/photo-storage-error"));
        assertThat(body.getTitle()).isEqualTo("Photo Storage Error");
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getDetail()).isEqualTo("MIME type no soportado");
    }
}
