package ru.yandex.practicum.comments.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.comments.service.CommentService;

// Внутренний API для других сервисов (через Feign). В gateway не выставляется
@RestController
@RequestMapping("/internal/comments")
@RequiredArgsConstructor
public class InternalCommentController {

    private final CommentService commentService;

    @DeleteMapping("/by-author/{authorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteByAuthor(@PathVariable Long authorId) {
        commentService.purgeAuthorComments(authorId);
    }
}
