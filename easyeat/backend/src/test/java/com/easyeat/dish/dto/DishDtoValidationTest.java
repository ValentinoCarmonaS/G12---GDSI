package com.easyeat.dish.dto;

import com.easyeat.dish.domain.DishCategory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Dish DTO Bean Validation Unit Test (T026)")
class DishDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("CreateDishRequest should pass validation when all fields are valid")
    void createDishRequest_shouldPass_whenValid() {
        CreateDishRequest request = new CreateDishRequest(
                "Risotto de Hongos",
                DishCategory.PLATOS_PRINCIPALES,
                "Delicioso risotto con caldo casero."
        );

        Set<ConstraintViolation<CreateDishRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CreateDishRequest should fail when name is blank or null")
    void createDishRequest_shouldFail_whenNameIsBlank() {
        CreateDishRequest request = new CreateDishRequest(
                "   ",
                DishCategory.PLATOS_PRINCIPALES,
                null
        );

        Set<ConstraintViolation<CreateDishRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    @DisplayName("CreateDishRequest should fail when name exceeds 100 characters")
    void createDishRequest_shouldFail_whenNameExceeds100Chars() {
        CreateDishRequest request = new CreateDishRequest(
                "A".repeat(101),
                DishCategory.PLATOS_PRINCIPALES,
                null
        );

        Set<ConstraintViolation<CreateDishRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    @DisplayName("CreateDishRequest should fail when category is null")
    void createDishRequest_shouldFail_whenCategoryIsNull() {
        CreateDishRequest request = new CreateDishRequest(
                "Plato Válido",
                null,
                null
        );

        Set<ConstraintViolation<CreateDishRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
    }

    @Test
    @DisplayName("CreateDishRequest should fail when description exceeds 500 characters")
    void createDishRequest_shouldFail_whenDescriptionExceeds500Chars() {
        CreateDishRequest request = new CreateDishRequest(
                "Plato Válido",
                DishCategory.PLATOS_PRINCIPALES,
                "D".repeat(501)
        );

        Set<ConstraintViolation<CreateDishRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
    }

    @Test
    @DisplayName("UpdateDishRequest should fail when name is blank")
    void updateDishRequest_shouldFail_whenNameIsBlank() {
        UpdateDishRequest request = new UpdateDishRequest(
                "",
                DishCategory.ENTRADAS,
                null
        );

        Set<ConstraintViolation<UpdateDishRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("RecipeUpdateRequest should pass when ingredients list is valid")
    void recipeUpdateRequest_shouldPass_whenValid() {
        RecipeUpdateRequest request = new RecipeUpdateRequest(java.util.List.of(
                new RecipeIngredientItem(java.util.UUID.randomUUID(), 100.0)
        ));

        Set<ConstraintViolation<RecipeUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("RecipeUpdateRequest should fail when ingredients list is null")
    void recipeUpdateRequest_shouldFail_whenIngredientsNull() {
        RecipeUpdateRequest request = new RecipeUpdateRequest(null);

        Set<ConstraintViolation<RecipeUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("ingredients"));
    }

    @Test
    @DisplayName("RecipeIngredientItem should fail when quantity is zero or negative")
    void recipeIngredientItem_shouldFail_whenQuantityZeroOrNegative() {
        RecipeIngredientItem itemZero = new RecipeIngredientItem(java.util.UUID.randomUUID(), 0.0);
        Set<ConstraintViolation<RecipeIngredientItem>> violationsZero = validator.validate(itemZero);
        assertThat(violationsZero).isNotEmpty();
        assertThat(violationsZero).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));

        RecipeIngredientItem itemNeg = new RecipeIngredientItem(java.util.UUID.randomUUID(), -5.0);
        Set<ConstraintViolation<RecipeIngredientItem>> violationsNeg = validator.validate(itemNeg);
        assertThat(violationsNeg).isNotEmpty();
        assertThat(violationsNeg).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
    }

    @Test
    @DisplayName("RecipeIngredientItem should fail when ingredientId is null")
    void recipeIngredientItem_shouldFail_whenIngredientIdNull() {
        RecipeIngredientItem item = new RecipeIngredientItem(null, 10.0);
        Set<ConstraintViolation<RecipeIngredientItem>> violations = validator.validate(item);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("ingredientId"));
    }
}
