package ru.yandex.practicum.comments.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.yandex.practicum.comments.client.EventGateway;
import ru.yandex.practicum.comments.client.UserGateway;
import ru.yandex.practicum.comments.dto.CommentDto;
import ru.yandex.practicum.comments.dto.NewCommentDto;
import ru.yandex.practicum.comments.mapper.CommentMapper;
import ru.yandex.practicum.comments.model.Comment;
import ru.yandex.practicum.comments.repository.CommentRepository;
import ru.yandex.practicum.common.event.EventInternalDto;
import ru.yandex.practicum.common.exceptions.ConflictException;
import ru.yandex.practicum.common.exceptions.NotFoundException;
import ru.yandex.practicum.common.user.UserDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserGateway userGateway;
    private final EventGateway eventGateway;
    private final TransactionTemplate tx;

    @Override
    public CommentDto createComment(Long userId, Long eventId, NewCommentDto dto) {
        UserDto author = userGateway.getUser(userId);
        EventInternalDto event = eventGateway.getEvent(eventId);

        if (!"PUBLISHED".equals(event.getState())) {
            throw new ConflictException("Комментировать можно только опубликованные события");
        }

        Comment comment = CommentMapper.toEntity(dto, author.getId(), eventId);
        comment.setPublishedDate(LocalDateTime.now());
        Comment saved = commentRepository.save(comment);   // короткая tx

        log.info("Пользователь {} добавил комментарий к событию {}", userId, eventId);
        return CommentMapper.toDto(saved, author.getName());
    }

    @Override
    public void deleteComment(Long userId, Long commentId) {
        userGateway.getUser(userId);
        Comment comment = findCommentOrRaiseException(commentId);

        if (!comment.getAuthorId().equals(userId)) {
            throw new ConflictException("Удалять можно только свои комментарии");
        }
        commentRepository.deleteById(commentId);
        log.info("Пользователь {} удалил комментарий {}", userId, commentId);
    }

    @Override
    public void deleteCommentById(Long commentId) {
        Comment comment = findCommentOrRaiseException(commentId);
        commentRepository.delete(comment);
        log.info("Администратор удалил комментарий {}", commentId);
    }

    @Override
    public void deleteAllCommentsByAuthor(Long authorId) {
        userGateway.getUser(authorId);
        commentRepository.deleteAllByAuthorId(authorId);
        log.info("Комментарии автора {} удалены", authorId);
    }

    @Override
    public void deleteAllCommentsByEvent(Long eventId) {
        eventGateway.getEvent(eventId);
        commentRepository.deleteAllByEventId(eventId);
        log.info("Комментарии к событию {} удалены", eventId);
    }

    @Override
    public CommentDto updateComment(Long userId, Long commentId, NewCommentDto dto) {
        UserDto author = userGateway.getUser(userId);          // HTTP вне транзакции
        // открываем транзакцию
        Comment saved = tx.execute(status -> {      // tx только на работу с БД
            Comment comment = findCommentOrRaiseException(commentId);
            if (!comment.getAuthorId().equals(userId)) {
                throw new ConflictException("Редактировать можно только свои комментарии");
            }
            comment.setText(dto.getText());
            comment.setEditedOn(LocalDateTime.now());
            return commentRepository.save(comment);
        });

        log.info("Пользователь {} отредактировал комментарий {}", userId, commentId);
        return CommentMapper.toDto(saved, author.getName());
    }

    @Override
    public CommentDto getComment(Long eventId, Long commentId) {
        checkEventIfAvailable(eventId);
        Comment comment = findCommentOrRaiseException(commentId);

        if (!comment.getEventId().equals(eventId)) {
            throw new NotFoundException("Комментарий с id=" + commentId + " не найден для события " + eventId);
        }
        return toDtos(List.of(comment)).get(0);
    }

    @Override
    public List<CommentDto> getAllCommentsByEvent(Long eventId, Integer from, Integer size) {
        checkEventIfAvailable(eventId);
        Pageable pageable = PageRequest.of(from / size, size);
        return toDtos(commentRepository.findAllByEventIdOrderByPublishedDateDesc(eventId, pageable));
    }

    @Override
    public List<CommentDto> getAllCommentsByAuthor(Long authorId, Integer from, Integer size) {
        UserDto author = userGateway.getUser(authorId);
        Pageable pageable = PageRequest.of(from / size, size);
        return commentRepository.findAllByAuthorIdOrderByPublishedDateDesc(authorId, pageable)
                .stream()
                .map(c -> CommentMapper.toDto(c, author.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public void purgeAuthorComments(Long authorId) {
        commentRepository.deleteAllByAuthorId(authorId);
        log.info("Комментарии автора {} удалены (внутренний вызов)", authorId);
    }

    // Имена авторов для списка комментариев: один запрос в user-service.
    // Если автор уже удалён, authorName будет null
    private List<CommentDto> toDtos(List<Comment> comments) {
        Set<Long> authorIds = comments.stream()
                .map(Comment::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, UserDto> users = userGateway.getUsersMap(authorIds);

        return comments.stream()
                .map(c -> CommentMapper.toDto(
                        c,
                        users.containsKey(c.getAuthorId()) ? users.get(c.getAuthorId()).getName() : null))
                .collect(Collectors.toList());
    }

    private Comment findCommentOrRaiseException(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий с id = " + commentId + " не найден!"));
    }

    // Чтение не должно падать из-за недоступности event-service: 404 пробрасываем, остальное логируем
    private void checkEventIfAvailable(Long eventId) {
        try {
            eventGateway.getEvent(eventId);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("event-service недоступен, пропускаю проверку события {}: {}", eventId, e.getMessage());
        }
    }
}
