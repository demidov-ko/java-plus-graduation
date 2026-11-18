package ru.yandex.practicum.comment.mapper;

import evm.main.comment.dto.CommentDto;
import evm.main.comment.dto.NewCommentDto;
import evm.main.comment.model.Comment;
import evm.main.event.model.Event;

public class CommentMapper {

    private CommentMapper() {
    }

    public static Comment toEntity(NewCommentDto dto, Long authorId, Event event) {
        return Comment.builder()
                .text(dto.getText())
                .event(event)
                .authorId(authorId)
                .build();
    }

    public static CommentDto toDto(Comment comment, String authorName) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText().trim())
                .eventId(comment.getEvent().getId())
                .authorId(comment.getAuthorId())
                .authorName(authorName)
                .publishedDate(comment.getPublishedDate())
                .editedOn(comment.getEditedOn())
                .build();
    }
}
