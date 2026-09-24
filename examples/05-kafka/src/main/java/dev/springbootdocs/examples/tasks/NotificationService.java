package dev.springbootdocs.examples.tasks;

import java.util.List;

public interface NotificationService {

    List<NotificationResponse> findAll(User currentUser);
}
