package ru.yandex.practicum.comments.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.comments.model.Comment;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findAllByEventIdOrderByPublishedDateDesc(Long eventId, Pageable pageable);

    List<Comment> findAllByAuthorIdOrderByPublishedDateDesc(Long authorId, Pageable pageable);

    @Transactional
    @Modifying
    @Query("DELETE FROM Comment c WHERE c.authorId = :authorId")
    void deleteAllByAuthorId(@Param("authorId") Long authorId);

    @Transactional
    @Modifying
    @Query("DELETE FROM Comment c WHERE c.eventId = :eventId")
    void deleteAllByEventId(@Param("eventId") Long eventId);
}
