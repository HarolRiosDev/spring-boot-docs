package dev.springbootdocs.examples.tasks.exception;

import java.util.List;

public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String campo, List<String> permitidos) {
        super("No se puede ordenar por '" + campo + "'. Campos permitidos: " + String.join(", ", permitidos));
    }
}
