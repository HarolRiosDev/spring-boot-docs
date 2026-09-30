package dev.springbootdocs.examples.tasks.dto;

import java.time.LocalDate;

// JdbcClient rellena este record directamente con las columnas del SELECT (dia, creadas, completadas)
public record DailyTaskCount(LocalDate dia, long creadas, long completadas) {
}
