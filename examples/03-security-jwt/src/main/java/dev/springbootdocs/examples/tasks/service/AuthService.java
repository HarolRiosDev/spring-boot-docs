package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.AuthResponse;
import dev.springbootdocs.examples.tasks.dto.LoginRequest;
import dev.springbootdocs.examples.tasks.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
