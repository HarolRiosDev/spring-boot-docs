package dev.springbootdocs.examples.tasks.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SiNoConverterTest {

    private final SiNoConverter converter = new SiNoConverter();

    @Test
    void booleanToDatabase_usesSAndN() {
        assertThat(converter.convertToDatabaseColumn(true)).isEqualTo("S");
        assertThat(converter.convertToDatabaseColumn(false)).isEqualTo("N");
    }

    @Test
    void databaseToBoolean_readsSAndN() {
        assertThat(converter.convertToEntityAttribute("S")).isTrue();
        assertThat(converter.convertToEntityAttribute("N")).isFalse();
    }

    @Test
    void null_staysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void unknownDatabaseValue_throwsClearException() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'X'");
    }
}
