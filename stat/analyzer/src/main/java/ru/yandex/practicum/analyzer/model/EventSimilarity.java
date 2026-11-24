package ru.yandex.practicum.analyzer.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "event_similarity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSimilarity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_a", nullable = false)
    private Long eventA;

    @Column(name = "event_b", nullable = false)
    private Long eventB;

    @Column(nullable = false)
    private Double score;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
