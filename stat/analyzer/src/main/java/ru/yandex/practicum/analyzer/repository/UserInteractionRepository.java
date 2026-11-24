package ru.yandex.practicum.analyzer.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.analyzer.model.UserInteraction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserInteractionRepository extends JpaRepository<UserInteraction, Long> {

    Optional<UserInteraction> findByUserIdAndEventId(Long userId, Long eventId);

    List<UserInteraction> findAllByUserId(Long userId);

    List<UserInteraction> findAllByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    @Query("select ui.eventId as eventId, sum(ui.weight) as total " +
            "from UserInteraction ui where ui.eventId in :ids group by ui.eventId")
    List<EventWeightSum> sumWeightsByEventIds(@Param("ids") Collection<Long> ids);

    interface EventWeightSum {
        Long getEventId();

        Double getTotal();
    }
}
