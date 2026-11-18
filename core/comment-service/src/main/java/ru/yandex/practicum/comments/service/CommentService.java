package ru.yandex.practicum.comments.service;

import ru.yandex.practicum.comments.dto.CommentDto;
import ru.yandex.practicum.comments.dto.NewCommentDto;

import java.util.List;

public interface CommentService {

    CommentDto createComment(Long userId, Long eventId, NewCommentDto dto);

    void deleteComment(Long userId, Long commentId);

    void deleteCommentById(Long commentId);

    void deleteAllCommentsByAuthor(Long authorId);

    void deleteAllCommentsByEvent(Long eventId);

    CommentDto updateComment(Long userId, Long commentId, NewCommentDto dto);

    CommentDto getComment(Long eventId, Long commentId);

    List<CommentDto> getAllCommentsByEvent(Long eventId, Integer from, Integer size);

    List<CommentDto> getAllCommentsByAuthor(Long authorId, Integer from, Integer size);

    // Внутренний API: удаление без проверки автора, потому что к этому моменту пользователь уже может быть удалён
    void purgeAuthorComments(Long authorId);

}