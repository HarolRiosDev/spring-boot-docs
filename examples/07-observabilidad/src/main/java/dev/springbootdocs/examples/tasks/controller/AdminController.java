package dev.springbootdocs.examples.tasks.controller;

import dev.springbootdocs.examples.tasks.dto.UserSummary;
import dev.springbootdocs.examples.tasks.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Administración", description = "Endpoints solo para el rol ADMIN")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/admin/users")
    @Operation(summary = "Listar todos los usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserSummary> listUsers() {
        return userService.findAll();
    }
}
