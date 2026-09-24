package dev.springbootdocs.examples.tasks.exception;

public class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException(String username) {
        super("Ya existe un usuario con el nombre " + username);
    }
}
