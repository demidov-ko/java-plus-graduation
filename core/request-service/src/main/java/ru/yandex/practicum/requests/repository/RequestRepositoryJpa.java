package ru.yandex.practicum.requests.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.requests.model.Request;
import ru.yandex.practicum.requests.model.Status;

import java.util.List;

public interface RequestRepositoryJpa extends JpaRepository<Request, Long> {

    @Query("select r from Request as r where r.requesterId = ?1")
    List<Request> findAllByUserId(Long userId);

    boolean existsByEventIdAndRequesterId(Long eventId, Long requesterId);

    long countByEventIdAndStatus(Long eventId, Status status);

    // Количество подтверждённых заявок для каждого события из списка
    @Query("SELECT r.eventId, COUNT(r) " +
            "FROM Request r " +
            "WHERE r.eventId IN :eventIds " +
            "AND r.status = 'CONFIRMED' " +
            "GROUP BY r.eventId")
    List<Object[]> countConfirmedByEventIds(@Param("eventIds") List<Long> eventIds);

    // Все заявки на конкретное событие
    List<Request> findAllByEventId(Long eventId);

    // Заявки по списку ID, отфильтрованные по принадлежности к конкретному событию
    @Query("SELECT r " +
            "FROM Request r " +
            "WHERE r.id IN :ids " +
            "AND r.eventId = :eventId")
    List<Request> findAllByIdInAndEventId(@Param("ids") List<Long> ids, @Param("eventId") Long eventId);

    boolean existsByEventIdAndRequesterIdAndStatus(Long eventId, Long requesterId, Status status);

}
