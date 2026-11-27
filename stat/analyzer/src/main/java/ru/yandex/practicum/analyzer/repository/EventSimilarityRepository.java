package ru.yandex.practicum.analyzer.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.analyzer.model.EventSimilarity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, Long> {

    Optional<EventSimilarity> findByEventAAndEventB(Long eventA, Long eventB);

    @Query("select s from EventSimilarity s where s.eventA in :ids or s.eventB in :ids")
    List<EventSimilarity> findAllByEvents(@Param("ids") Collection<Long> ids);

    @Query("""
        select s from EventSimilarity s
        where (s.eventA = :eventId and s.eventB not in :excluded)
           or (s.eventB = :eventId and s.eventA not in :excluded)
        order by s.score desc
        """)
    List<EventSimilarity> findTopSimilar(@Param("eventId") Long eventId,
                                         @Param("excluded") Collection<Long> excluded,
                                         Pageable pageable);
}
