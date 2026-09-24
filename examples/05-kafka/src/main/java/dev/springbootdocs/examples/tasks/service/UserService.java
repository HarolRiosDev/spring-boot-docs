package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.UserSummary;
import java.util.List;

public interface UserService {

    List<UserSummary> findAll();
}
