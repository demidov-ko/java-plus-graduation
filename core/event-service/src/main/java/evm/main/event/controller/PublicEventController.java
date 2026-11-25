package evm.main.event.controller;

import evm.main.event.dto.EventFullDto;
import evm.main.event.dto.EventShortDto;
import evm.main.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

// Публичный API — доступен всем без авторизации
@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class PublicEventController {

    private final EventService eventService;

    // Список событий с фильтрацией
    // Возвращает только PUBLISHED события
    @GetMapping
    public List<EventShortDto> getEvents(
            // Текстовый поиск по аннотации и описанию (без учёта регистра)
            @RequestParam(required = false) String text,
            // Фильтр по категориям
            @RequestParam(required = false) List<Long> categories,
            // Только платные или бесплатные события
            @RequestParam(required = false) Boolean paid,
            // Диапазон дат — если не указан, берём события позже текущего момента
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
            // Только события где ещё есть свободные места
            @RequestParam(defaultValue = "false") Boolean onlyAvailable,
            // Сортировка: EVENT_DATE или VIEWS
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") Integer from,
            @RequestParam(defaultValue = "10") Integer size) {

        log.info("GET /events — text={}, categories={}, paid={}, sort={}",
                text, categories, paid, sort);

        return eventService.getPublicEvents(
                text, categories, paid, rangeStart, rangeEnd,
                onlyAvailable, sort, from, size);
    }

    // Рекомендации для пользователя. Литеральный путь приоритетнее /events/{id}
    @GetMapping("/recommendations")
    public List<EventShortDto> getRecommendations(
            @RequestHeader("X-EWM-USER-ID") long userId,
            @RequestParam(defaultValue = "10") Integer maxResults) {

        log.info("GET /events/recommendations — userId={}", userId);
        return eventService.getRecommendations(userId, maxResults);
    }

    // Подробная информация об опубликованном событии.
    // Каждый просмотр фиксируется как действие пользователя
    @GetMapping("/{id}")
    public EventFullDto getEvent(@PathVariable Long id,
                                 @RequestHeader("X-EWM-USER-ID") long userId) {
        log.info("GET /events/{} — userId={}", id, userId);
        return eventService.getPublicEventById(id, userId);
    }

    // Лайк мероприятия (только посещённые)
    @PutMapping("/{eventId}/like")
    public void likeEvent(@PathVariable Long eventId,
                          @RequestHeader("X-EWM-USER-ID") long userId) {
        log.info("PUT /events/{}/like — userId={}", eventId, userId);
        eventService.likeEvent(userId, eventId);
    }
}
