package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.CommentRequest;
import dev.springbootdocs.examples.tasks.dto.CommentResponse;
import java.util.List;

public interface TaskCommentService {

    List<CommentResponse> findByTask(Long taskId);

    CommentResponse add(Long taskId, CommentRequest request);
}
