package com.easyeat.catalog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EstablishmentStatus Domain Enum Tests (T007)")
class EstablishmentStatusTest {

    @Test
    @DisplayName("Should contain exactly ACTIVE, PAUSED, and DISABLED values")
    void shouldContainExpectedEnumValues() {
        assertThat(EstablishmentStatus.values()).containsExactly(
                EstablishmentStatus.ACTIVE,
                EstablishmentStatus.PAUSED,
                EstablishmentStatus.DISABLED
        );
    }

    @Test
    @DisplayName("Should parse valid status strings via valueOf")
    void shouldParseValidStatusStrings() {
        assertThat(EstablishmentStatus.valueOf("ACTIVE")).isEqualTo(EstablishmentStatus.ACTIVE);
        assertThat(EstablishmentStatus.valueOf("PAUSED")).isEqualTo(EstablishmentStatus.PAUSED);
        assertThat(EstablishmentStatus.valueOf("DISABLED")).isEqualTo(EstablishmentStatus.DISABLED);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for unknown status strings")
    void shouldRejectUnknownStatusStrings() {
        assertThatThrownBy(() -> EstablishmentStatus.valueOf("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
