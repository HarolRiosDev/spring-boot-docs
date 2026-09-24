package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.NotificationResponse;
import dev.springbootdocs.examples.tasks.model.User;
import java.util.List;

public interface NotificationService {

    List<NotificationResponse> findAll(User currentUser);
}
