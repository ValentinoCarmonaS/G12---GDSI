package com.easyeat.dish.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DishEnumsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DishCategoryConverter converter = new DishCategoryConverter();

    @Test
    @DisplayName("DishCategory should define all 5 closed database categories")
    void dishCategoryValuesAndLabels() {
        assertThat(DishCategory.values()).containsExactly(
                DishCategory.ENTRADAS,
                DishCategory.PLATOS_PRINCIPALES,
                DishCategory.POSTRES,
                DishCategory.BEBIDAS,
                DishCategory.ENSALADAS
        );

        assertThat(DishCategory.ENTRADAS.getValue()).isEqualTo("Entradas");
        assertThat(DishCategory.PLATOS_PRINCIPALES.getValue()).isEqualTo("Platos principales");
        assertThat(DishCategory.POSTRES.getValue()).isEqualTo("Postres");
        assertThat(DishCategory.BEBIDAS.getValue()).isEqualTo("Bebidas");
        assertThat(DishCategory.ENSALADAS.getValue()).isEqualTo("Ensaladas");
    }

    @ParameterizedTest
    @CsvSource({
            "Entradas, ENTRADAS",
            "entradas, ENTRADAS",
            "ENTRADAS, ENTRADAS",
            "'  Entradas  ', ENTRADAS",
            "Platos principales, PLATOS_PRINCIPALES",
            "platos principales, PLATOS_PRINCIPALES",
            "PLATOS_PRINCIPALES, PLATOS_PRINCIPALES",
            "platos_principales, PLATOS_PRINCIPALES",
            "Postres, POSTRES",
            "postres, POSTRES",
            "POSTRES, POSTRES",
            "Bebidas, BEBIDAS",
            "bebidas, BEBIDAS",
            "BEBIDAS, BEBIDAS",
            "Ensaladas, ENSALADAS",
            "ensaladas, ENSALADAS",
            "ENSALADAS, ENSALADAS"
    })
    @DisplayName("DishCategory.fromValue should parse valid values and names case-insensitively")
    void fromValueValid(String rawValue, DishCategory expected) {
        assertThat(DishCategory.fromValue(rawValue)).isEqualTo(expected);
    }

    @Test
    @DisplayName("DishCategory.fromValue should return null for null input")
    void fromValueNull() {
        assertThat(DishCategory.fromValue(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Invalido", "Comidas rapidas", "Sopas"})
    @DisplayName("DishCategory.fromValue should throw IllegalArgumentException for invalid input")
    void fromValueInvalid(String invalidValue) {
        assertThatThrownBy(() -> DishCategory.fromValue(invalidValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid dish category: " + invalidValue);
    }

    @Test
    @DisplayName("DishCategory should serialize to JSON using its display value")
    void dishCategoryJacksonSerialization() throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(DishCategory.PLATOS_PRINCIPALES);
        assertThat(json).isEqualTo("\"Platos principales\"");

        String jsonEntradas = objectMapper.writeValueAsString(DishCategory.ENTRADAS);
        assertThat(jsonEntradas).isEqualTo("\"Entradas\"");
    }

    @Test
    @DisplayName("DishCategory should deserialize from JSON using fromValue")
    void dishCategoryJacksonDeserialization() throws JsonProcessingException {
        DishCategory cat1 = objectMapper.readValue("\"Platos principales\"", DishCategory.class);
        assertThat(cat1).isEqualTo(DishCategory.PLATOS_PRINCIPALES);

        DishCategory cat2 = objectMapper.readValue("\"PLATOS_PRINCIPALES\"", DishCategory.class);
        assertThat(cat2).isEqualTo(DishCategory.PLATOS_PRINCIPALES);

        DishCategory cat3 = objectMapper.readValue("\"postres\"", DishCategory.class);
        assertThat(cat3).isEqualTo(DishCategory.POSTRES);

        assertThatThrownBy(() -> objectMapper.readValue("\"Desconocido\"", DishCategory.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DishCategoryConverter should correctly convert between entity attribute and database column")
    void dishCategoryConverter() {
        assertThat(converter.convertToDatabaseColumn(DishCategory.PLATOS_PRINCIPALES)).isEqualTo("Platos principales");
        assertThat(converter.convertToDatabaseColumn(DishCategory.BEBIDAS)).isEqualTo("Bebidas");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();

        assertThat(converter.convertToEntityAttribute("Platos principales")).isEqualTo(DishCategory.PLATOS_PRINCIPALES);
        assertThat(converter.convertToEntityAttribute("Postres")).isEqualTo(DishCategory.POSTRES);
        assertThat(converter.convertToEntityAttribute(null)).isNull();

        assertThatThrownBy(() -> converter.convertToEntityAttribute("Inexistente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DishStatus should define expected lifecycle states")
    void dishStatusValues() {
        assertThat(DishStatus.values()).containsExactly(
                DishStatus.DRAFT,
                DishStatus.PENDING_PRICE,
                DishStatus.PUBLISHED
        );

        assertThat(DishStatus.valueOf("DRAFT")).isEqualTo(DishStatus.DRAFT);
        assertThat(DishStatus.valueOf("PENDING_PRICE")).isEqualTo(DishStatus.PENDING_PRICE);
        assertThat(DishStatus.valueOf("PUBLISHED")).isEqualTo(DishStatus.PUBLISHED);
    }

    @Test
    @DisplayName("DishStatus constant names must match the chk_dishes_status database domain")
    void dishStatusNamesMatchDatabaseDomain() {
        assertThat(DishStatus.values())
                .extracting(Enum::name)
                .containsExactly("DRAFT", "PENDING_PRICE", "PUBLISHED");
    }

    @Test
    @DisplayName("DishStatus should serialize and deserialize with Jackson")
    void dishStatusJackson() throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(DishStatus.DRAFT);
        assertThat(json).isEqualTo("\"DRAFT\"");

        DishStatus status = objectMapper.readValue("\"PENDING_PRICE\"", DishStatus.class);
        assertThat(status).isEqualTo(DishStatus.PENDING_PRICE);

        assertThat(objectMapper.writeValueAsString(DishStatus.PUBLISHED)).isEqualTo("\"PUBLISHED\"");
        assertThat(objectMapper.readValue("\"PUBLISHED\"", DishStatus.class)).isEqualTo(DishStatus.PUBLISHED);
    }
}
