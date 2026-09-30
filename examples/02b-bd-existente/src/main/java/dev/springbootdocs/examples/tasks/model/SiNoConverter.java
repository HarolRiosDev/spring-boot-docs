package dev.springbootdocs.examples.tasks.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Traduce las banderas heredadas 'S'/'N' de la base de datos a boolean y viceversa.
 */
@Converter
public class SiNoConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? "S" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        return switch (dbValue) {
            case "S" -> true;
            case "N" -> false;
            default -> throw new IllegalArgumentException(
                    "Se esperaba 'S' o 'N' en la base de datos y llegó '" + dbValue + "'");
        };
    }
}
