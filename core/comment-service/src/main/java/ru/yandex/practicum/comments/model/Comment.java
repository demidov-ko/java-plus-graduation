package ru.yandex.practicum.comments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "comments")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(nullable = false, length = 4000)
    private String text;

    @Column(name = "published_date", nullable = false)
    private LocalDateTime publishedDate;

    @Column(name = "edited_on")
    private LocalDateTime editedOn;

    @Column(name = "author_id", nullable = false)
    private Long authorId;   // id автора в user-service

    @Column(name = "event_id", nullable = false)
    private Long eventId;    // id события в сервисе событий
}
