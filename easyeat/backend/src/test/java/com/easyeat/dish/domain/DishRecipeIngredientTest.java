package com.easyeat.dish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DishRecipeIngredientTest {

    @Nested
    @DisplayName("DishRecipeIngredientId tests")
    class DishRecipeIngredientIdTests {

        @Test
        @DisplayName("Default constructor and accessors")
        void defaultConstructorAndAccessors() {
            DishRecipeIngredientId id = new DishRecipeIngredientId();
            assertThat(id.getDishId()).isNull();
            assertThat(id.getIngredientId()).isNull();

            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();

            id.setDishId(dishId);
            id.setIngredientId(ingredientId);

            assertThat(id.getDishId()).isEqualTo(dishId);
            assertThat(id.getIngredientId()).isEqualTo(ingredientId);
        }

        @Test
        @DisplayName("Parameterized constructor sets fields")
        void parameterizedConstructor() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();

            DishRecipeIngredientId id = new DishRecipeIngredientId(dishId, ingredientId);

            assertThat(id.getDishId()).isEqualTo(dishId);
            assertThat(id.getIngredientId()).isEqualTo(ingredientId);
        }

        @Test
        @DisplayName("Equals and hashCode contract")
        void equalsAndHashCodeContract() {
            UUID dishId1 = UUID.randomUUID();
            UUID ingredientId1 = UUID.randomUUID();
            UUID dishId2 = UUID.randomUUID();
            UUID ingredientId2 = UUID.randomUUID();

            DishRecipeIngredientId id1 = new DishRecipeIngredientId(dishId1, ingredientId1);
            DishRecipeIngredientId id1Copy = new DishRecipeIngredientId(dishId1, ingredientId1);
            DishRecipeIngredientId idDiffDish = new DishRecipeIngredientId(dishId2, ingredientId1);
            DishRecipeIngredientId idDiffIngredient = new DishRecipeIngredientId(dishId1, ingredientId2);

            // Reflexive
            assertThat(id1).isEqualTo(id1);
            // Symmetric
            assertThat(id1).isEqualTo(id1Copy);
            assertThat(id1Copy).isEqualTo(id1);
            assertThat(id1.hashCode()).isEqualTo(id1Copy.hashCode());

            // Non-equality
            assertThat(id1).isNotEqualTo(idDiffDish);
            assertThat(id1).isNotEqualTo(idDiffIngredient);
            assertThat(id1).isNotEqualTo(null);
            assertThat(id1).isNotEqualTo("some-string");
        }

        @Test
        @DisplayName("toString contains field values")
        void testToString() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();

            DishRecipeIngredientId id = new DishRecipeIngredientId(dishId, ingredientId);
            String str = id.toString();

            assertThat(str).contains("DishRecipeIngredientId", dishId.toString(), ingredientId.toString());
        }

        @Test
        @DisplayName("JPA annotations verification on DishRecipeIngredientId")
        void jpaAnnotations() throws NoSuchFieldException {
            assertThat(DishRecipeIngredientId.class.isAnnotationPresent(Embeddable.class)).isTrue();

            Field dishIdField = DishRecipeIngredientId.class.getDeclaredField("dishId");
            Column dishIdColumn = dishIdField.getAnnotation(Column.class);
            assertThat(dishIdColumn).isNotNull();
            assertThat(dishIdColumn.name()).isEqualTo("dish_id");
            assertThat(dishIdColumn.nullable()).isFalse();

            Field ingredientIdField = DishRecipeIngredientId.class.getDeclaredField("ingredientId");
            Column ingredientIdColumn = ingredientIdField.getAnnotation(Column.class);
            assertThat(ingredientIdColumn).isNotNull();
            assertThat(ingredientIdColumn.name()).isEqualTo("ingredient_id");
            assertThat(ingredientIdColumn.nullable()).isFalse();
        }
    }

    @Nested
    @DisplayName("DishRecipeIngredient tests")
    class DishRecipeIngredientEntityTests {

        @Test
        @DisplayName("Default constructor initializes defaults")
        void defaultConstructor() {
            DishRecipeIngredient item = new DishRecipeIngredient();

            assertThat(item.getId()).isNotNull();
            assertThat(item.getDishId()).isNull();
            assertThat(item.getIngredientId()).isNull();
            assertThat(item.getQuantity()).isNull();
            assertThat(item.getCreatedAt()).isNotNull();
            assertThat(item.getCreatedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
            assertThat(item.getUpdatedAt()).isNotNull();
            assertThat(item.getUpdatedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Constructor with DishRecipeIngredientId and quantity")
        void constructorWithIdAndQuantity() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            DishRecipeIngredientId id = new DishRecipeIngredientId(dishId, ingredientId);
            BigDecimal quantity = new BigDecimal("2.5000");

            DishRecipeIngredient item = new DishRecipeIngredient(id, quantity);

            assertThat(item.getId()).isEqualTo(id);
            assertThat(item.getDishId()).isEqualTo(dishId);
            assertThat(item.getIngredientId()).isEqualTo(ingredientId);
            assertThat(item.getQuantity()).isEqualTo(quantity);
            assertThat(item.getCreatedAt()).isNotNull();
            assertThat(item.getUpdatedAt()).isNotNull();

            // When id is null in constructor, default id is initialized
            DishRecipeIngredient itemNullId = new DishRecipeIngredient((DishRecipeIngredientId) null, quantity);
            assertThat(itemNullId.getId()).isNotNull();
        }

        @Test
        @DisplayName("Constructor with dishId, ingredientId, and quantity")
        void constructorWithDishIdIngredientIdAndQuantity() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            BigDecimal quantity = new BigDecimal("100.0000");

            DishRecipeIngredient item = new DishRecipeIngredient(dishId, ingredientId, quantity);

            assertThat(item.getId()).isNotNull();
            assertThat(item.getDishId()).isEqualTo(dishId);
            assertThat(item.getIngredientId()).isEqualTo(ingredientId);
            assertThat(item.getQuantity()).isEqualTo(quantity);
            assertThat(item.getCreatedAt()).isNotNull();
            assertThat(item.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Getters, setters, and helper methods")
        void gettersSettersAndHelpers() {
            DishRecipeIngredient item = new DishRecipeIngredient();

            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            BigDecimal quantity = new BigDecimal("3.0000");
            Instant past = Instant.now().minus(2, ChronoUnit.DAYS);

            item.setDishId(dishId);
            item.setIngredientId(ingredientId);
            item.setQuantity(quantity);
            item.setCreatedAt(past);
            item.setUpdatedAt(past);

            assertThat(item.getDishId()).isEqualTo(dishId);
            assertThat(item.getIngredientId()).isEqualTo(ingredientId);
            assertThat(item.getQuantity()).isEqualTo(quantity);
            assertThat(item.getCreatedAt()).isEqualTo(past);
            assertThat(item.getUpdatedAt()).isEqualTo(past);

            // Test setting null id
            item.setId(null);
            assertThat(item.getId()).isNotNull(); // Replaces null with new instance

            // Set helper fields when id starts empty
            UUID newDishId = UUID.randomUUID();
            UUID newIngredientId = UUID.randomUUID();
            item.setDishId(newDishId);
            item.setIngredientId(newIngredientId);

            assertThat(item.getDishId()).isEqualTo(newDishId);
            assertThat(item.getIngredientId()).isEqualTo(newIngredientId);
        }

        @Test
        @DisplayName("Helper accessors return null when id is null")
        void helpersWhenIdIsNull() throws NoSuchFieldException, IllegalAccessException {
            DishRecipeIngredient item = new DishRecipeIngredient();
            Field idField = DishRecipeIngredient.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(item, null);

            assertThat(item.getDishId()).isNull();
            assertThat(item.getIngredientId()).isNull();

            UUID dishId = UUID.randomUUID();
            item.setDishId(dishId);
            assertThat(item.getDishId()).isEqualTo(dishId);

            idField.set(item, null);
            UUID ingredientId = UUID.randomUUID();
            item.setIngredientId(ingredientId);
            assertThat(item.getIngredientId()).isEqualTo(ingredientId);
        }

        @Test
        @DisplayName("PrePersist and PreUpdate callbacks")
        void lifecycleCallbacks() {
            DishRecipeIngredient item = new DishRecipeIngredient();
            item.setCreatedAt(null);
            item.setUpdatedAt(null);

            item.onPrePersist();
            assertThat(item.getCreatedAt()).isNotNull();
            assertThat(item.getUpdatedAt()).isNotNull();

            Instant initialUpdated = item.getUpdatedAt();
            item.onPreUpdate();
            assertThat(item.getUpdatedAt()).isNotNull();
            assertThat(item.getUpdatedAt()).isAfterOrEqualTo(initialUpdated);
        }

        @Test
        @DisplayName("Equals and hashCode contract")
        void equalsAndHashCodeContract() {
            UUID dishId1 = UUID.randomUUID();
            UUID ingredientId1 = UUID.randomUUID();
            UUID dishId2 = UUID.randomUUID();
            UUID ingredientId2 = UUID.randomUUID();

            DishRecipeIngredient item1 = new DishRecipeIngredient(dishId1, ingredientId1, BigDecimal.TEN);
            DishRecipeIngredient item1Copy = new DishRecipeIngredient(dishId1, ingredientId1, BigDecimal.ONE);
            DishRecipeIngredient item2 = new DishRecipeIngredient(dishId2, ingredientId2, BigDecimal.TEN);

            // Reflexive
            assertThat(item1).isEqualTo(item1);
            // Symmetric based on composite PK id
            assertThat(item1).isEqualTo(item1Copy);
            assertThat(item1Copy).isEqualTo(item1);
            assertThat(item1.hashCode()).isEqualTo(item1Copy.hashCode());

            // Non-equality
            assertThat(item1).isNotEqualTo(item2);
            assertThat(item1).isNotEqualTo(null);
            assertThat(item1).isNotEqualTo("other-object");
        }

        @Test
        @DisplayName("toString contains entity info")
        void testToString() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            BigDecimal quantity = new BigDecimal("4.5000");

            DishRecipeIngredient item = new DishRecipeIngredient(dishId, ingredientId, quantity);
            String str = item.toString();

            assertThat(str).contains("DishRecipeIngredient", "quantity=4.5000", dishId.toString(), ingredientId.toString());
        }

        @Test
        @DisplayName("JPA annotations verification on DishRecipeIngredient")
        void jpaAnnotations() throws NoSuchFieldException {
            assertThat(DishRecipeIngredient.class.isAnnotationPresent(Entity.class)).isTrue();

            Table table = DishRecipeIngredient.class.getAnnotation(Table.class);
            assertThat(table).isNotNull();
            assertThat(table.name()).isEqualTo("dish_recipe_ingredients");

            Field idField = DishRecipeIngredient.class.getDeclaredField("id");
            assertThat(idField.isAnnotationPresent(EmbeddedId.class)).isTrue();

            Field quantityField = DishRecipeIngredient.class.getDeclaredField("quantity");
            Column quantityColumn = quantityField.getAnnotation(Column.class);
            assertThat(quantityColumn).isNotNull();
            assertThat(quantityColumn.name()).isEqualTo("quantity");
            assertThat(quantityColumn.nullable()).isFalse();
            assertThat(quantityColumn.precision()).isEqualTo(12);
            assertThat(quantityColumn.scale()).isEqualTo(4);

            Field createdAtField = DishRecipeIngredient.class.getDeclaredField("createdAt");
            Column createdAtColumn = createdAtField.getAnnotation(Column.class);
            assertThat(createdAtColumn).isNotNull();
            assertThat(createdAtColumn.name()).isEqualTo("created_at");
            assertThat(createdAtColumn.nullable()).isFalse();
            assertThat(createdAtColumn.updatable()).isFalse();

            Field updatedAtField = DishRecipeIngredient.class.getDeclaredField("updatedAt");
            Column updatedAtColumn = updatedAtField.getAnnotation(Column.class);
            assertThat(updatedAtColumn).isNotNull();
            assertThat(updatedAtColumn.name()).isEqualTo("updated_at");
            assertThat(updatedAtColumn.nullable()).isFalse();
        }
    }
}
