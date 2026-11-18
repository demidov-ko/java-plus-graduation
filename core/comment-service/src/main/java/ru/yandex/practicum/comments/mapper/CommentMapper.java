package ru.yandex.practicum.comments.mapper;

import ru.yandex.practicum.comments.dto.CommentDto;
import ru.yandex.practicum.comments.dto.NewCommentDto;
import ru.yandex.practicum.comments.model.Comment;

public class CommentMapper {

    private CommentMapper() {
    }

    public static Comment toEntity(NewCommentDto dto, Long authorId, Long eventId) {
        return Comment.builder()
                .text(dto.getText())
                .authorId(authorId)
                .eventId(eventId)
                .build();
    }

    public static CommentDto toDto(Comment comment, String authorName) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText().trim())
                .eventId(comment.getEventId())
                .authorId(comment.getAuthorId())
                .authorName(authorName)
                .publishedDate(comment.getPublishedDate())
                .editedOn(comment.getEditedOn())
                .build();
    }
}