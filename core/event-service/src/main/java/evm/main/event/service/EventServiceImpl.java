package evm.main.event.service;

import evm.main.category.model.Category;
import evm.main.category.repository.CategoryRepository;
import evm.main.client.RequestGateway;
import evm.main.client.UserGateway;
import evm.main.event.dto.*;
import evm.main.event.mapper.EventMapper;
import evm.main.event.mapper.LocationMapper;
import evm.main.event.model.Event;
import evm.main.event.model.EventState;
import evm.main.event.repository.EventRepository;
import evm.main.event.repository.EventSpecification;
import ru.yandex.practicum.common.exceptions.ConflictException;
import ru.yandex.practicum.common.exceptions.NotFoundException;
import evm.stat.client.AnalyzerClient;
import evm.stat.client.CollectorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.yandex.practicum.common.event.EventInternalDto;
import ru.yandex.practicum.common.user.UserDto;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private static final int MIN_HOURS_BEFORE_EVENT_USER = 2;
    private static final int MIN_HOURS_BEFORE_EVENT_ADMIN = 1;

    private final EventRepository eventRepository;
    private final UserGateway userGateway;
    private final RequestGateway requestGateway;
    private final CategoryRepository categoryRepository;
    private final CollectorClient collectorClient;
    private final AnalyzerClient analyzerClient;

// =========================================================================
// PUBLIC
// =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Boolean onlyAvailable, String sort,
                                               Integer from, Integer size) {
        // Валидация диапазона дат
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new IllegalArgumentException("rangeStart не может быть позже rangeEnd");
        }

        // Сортировка
        Pageable pageable;
        if ("EVENT_DATE".equals(sort)) {
            pageable = PageRequest.of(from / size, size,
                    Sort.by("eventDate").ascending());
        } else {
            pageable = PageRequest.of(from / size, size);
        }

        Specification<Event> spec = EventSpecification.publicFilter(
                text, categories, paid, rangeStart, rangeEnd);

        List<Event> events = eventRepository.findAll(spec, pageable).getContent();
        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(events);

        // Фильтрация по доступности
        if (onlyAvailable != null && onlyAvailable) {
            events = events.stream()
                    .filter(e -> e.getParticipantLimit() == 0 ||
                            e.getParticipantLimit() > confirmedMap.getOrDefault(e.getId(), 0L))
                    .collect(Collectors.toList());
        }

        // Рейтинг и инициаторы для всех событий разом
        Map<Long, Double> ratingsMap = getRatingsMap(events);
        Map<Long, UserShortDto> initiatorsMap = getInitiatorsMap(events);

        List<EventShortDto> result = events.stream()
                .map(e -> EventMapper.toShortDto(
                        e,
                        initiatorsMap.get(e.getInitiatorId()),
                        ratingsMap.getOrDefault(e.getId(), 0.0),
                        confirmedMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());

        // Сортировка по рейтингу
        if ("VIEWS".equals(sort)) {
            result.sort(Comparator.comparingDouble(EventShortDto::getRating).reversed());
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getPublicEventById(Long id, Long userId) {
        // Только опубликованные события доступны публично
        Event event = eventRepository.findByIdAndState(id, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + id + " не найдено"));

        // Просмотр отправляем только для существующего опубликованного события
        collectorClient.sendUserAction(userId, id, ActionTypeProto.ACTION_VIEW);

        Long confirmed = requestGateway.getConfirmedCount(id);
        return EventMapper.toFullDto(event, getInitiator(event.getInitiatorId()), getRating(id), confirmed);
    }

// =========================================================================
// PRIVATE
// =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size) {
        // Заодно проверяет, что пользователь существует
        UserShortDto initiator = EventMapper.toUserShortDto(userGateway.getUser(userId));

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable);

        Map<Long, Double> ratingsMap = getRatingsMap(events);
        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(events);

        return events.stream()
                .map(e -> EventMapper.toShortDto(
                        e,
                        initiator,
                        ratingsMap.getOrDefault(e.getId(), 0.0),
                        confirmedMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto addEvent(Long userId, NewEventDto dto) {
        // Проверяем что дата события не раньше чем через 2 часа
        validateEventDate(dto.getEventDate(), MIN_HOURS_BEFORE_EVENT_USER);

        UserDto user = userGateway.getUser(userId);
        Category category = getCategoryOrThrow(dto.getCategory());

        Event event = EventMapper.toEntity(dto, category, user.getId());
        Event saved = eventRepository.save(event);

        return EventMapper.toFullDto(saved, EventMapper.toUserShortDto(user), 0.0, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        UserDto user = userGateway.getUser(userId);
        Event event = getEventOrThrow(eventId);

        // Пользователь может видеть только своё событие
        if (!event.getInitiatorId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        double rating = getRating(eventId);
        Long confirmed = requestGateway.getConfirmedCount(eventId);

        return EventMapper.toFullDto(event, EventMapper.toUserShortDto(user), rating, confirmed);
    }

    @Override
    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId,
                                        UpdateEventUserRequest dto) {
        UserDto user = userGateway.getUser(userId);
        Event event = getEventOrThrow(eventId);

        // Пользователь может редактировать только своё событие
        if (!event.getInitiatorId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        // Нельзя редактировать опубликованное событие
        if (EventState.PUBLISHED.equals(event.getState())) {
            throw new ConflictException("Изменять можно только события в статусе PENDING или CANCELED");
        }

        // Применяем изменения — только те поля которые не null
        applyUserUpdates(event, dto);

        Event saved = eventRepository.save(event);
        Long confirmed = requestGateway.getConfirmedCount(eventId);

        return EventMapper.toFullDto(saved, EventMapper.toUserShortDto(user),
                getRating(saved.getId()), confirmed);
    }

// =========================================================================
// ADMIN
// =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<EventFullDto> getAdminEvents(List<Long> users, List<String> states,
                                             List<Long> categories,
                                             LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                             Integer from, Integer size) {

        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());

        Specification<Event> spec = EventSpecification.adminFilter(users, states, categories, rangeStart, rangeEnd);

        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        Map<Long, Double> ratingsMap = getRatingsMap(events);
        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(events);
        Map<Long, UserShortDto> initiatorsMap = getInitiatorsMap(events);

        return events.stream()
                .map(e -> EventMapper.toFullDto(
                        e,
                        initiatorsMap.get(e.getInitiatorId()),
                        ratingsMap.getOrDefault(e.getId(), 0.0),
                        confirmedMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto updateAdminEvent(Long eventId, UpdateEventAdminRequest dto) {
        Event event = getEventOrThrow(eventId);

        // Применяем изменения
        applyAdminUpdates(event, dto);

        Event saved = eventRepository.save(event);
        Long confirmed = requestGateway.getConfirmedCount(eventId);

        return EventMapper.toFullDto(saved, getInitiator(saved.getInitiatorId()),
                getRating(saved.getId()), confirmed);
    }

// =========================================================================
// INTERNAL
// =========================================================================

    @Override
    @Transactional(readOnly = true)
    public EventInternalDto getInternalEvent(Long eventId) {
        Event event = getEventOrThrow(eventId);
        return EventInternalDto.builder()
                .id(event.getId())
                .initiatorId(event.getInitiatorId())
                .state(event.getState().name())
                .participantLimit(event.getParticipantLimit())
                .requestModeration(event.getRequestModeration())
                .build();
    }

// =========================================================================
// РЕКОМЕНДАЦИИ И ЛАЙКИ
// =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getRecommendations(Long userId, Integer maxResults) {
        log.info("Запрос рекомендаций: пользователь={}, максимум={}", userId, maxResults);

        List<RecommendedEventProto> recommended = analyzerClient.getRecommendationsForUser(userId, maxResults);
        if (recommended.isEmpty()) {
            return List.of();
        }

        List<Long> ids = recommended.stream().map(RecommendedEventProto::getEventId).toList();
        Map<Long, Event> eventsById = eventRepository.findAllByIdInAndState(ids, EventState.PUBLISHED).stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        // порядок как в ответе Analyzer (от большей оценки к меньшей)
        List<Event> events = ids.stream().map(eventsById::get).filter(Objects::nonNull).toList();

        Map<Long, Double> ratingsMap = getRatingsMap(events);
        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(events);
        Map<Long, UserShortDto> initiatorsMap = getInitiatorsMap(events);

        return events.stream()
                .map(e -> EventMapper.toShortDto(
                        e,
                        initiatorsMap.get(e.getInitiatorId()),
                        ratingsMap.getOrDefault(e.getId(), 0.0),
                        confirmedMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    public void likeEvent(Long userId, Long eventId) {
        log.info("Лайк мероприятия: пользователь={}, мероприятие={}", userId, eventId);

        userGateway.getUser(userId);
        eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // лайкать можно только мероприятия, на которые у пользователя подтверждена заявка
        if (!requestGateway.isParticipant(userId, eventId)) {
            throw new IllegalArgumentException(
                    "Пользователь " + userId + " не посещал мероприятие " + eventId
                            + " и не может его лайкнуть");
        }
        collectorClient.sendUserAction(userId, eventId, ActionTypeProto.ACTION_LIKE);
    }

// =========================================================================
// Вспомогательные методы
// =========================================================================

    // Рейтинг одного события: сумма максимальных весов действий пользователей (Analyzer)
    private double getRating(Long eventId) {
        return analyzerClient.getInteractionsCount(List.of(eventId)).stream()
                .findFirst()
                .map(RecommendedEventProto::getScore)
                .orElse(0.0);
    }

    // Рейтинги для списка событий одним запросом. Если Analyzer недоступен, будут нули
    private Map<Long, Double> getRatingsMap(List<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = events.stream().map(Event::getId).toList();
        return analyzerClient.getInteractionsCount(ids).stream()
                .collect(Collectors.toMap(RecommendedEventProto::getEventId, RecommendedEventProto::getScore));
    }

    // Количество подтверждённых заявок для списка событий: один запрос в request-service
    // Возвращает Map: eventId → количество заявок
    private Map<Long, Long> getConfirmedRequestsMap(List<Event> events) {
        return requestGateway.getConfirmedMap(
                events.stream().map(Event::getId).collect(Collectors.toList()));
    }

    // Инициаторы для списка событий: один запрос в user-service
    // Возвращает Map: userId → краткая информация о пользователе
    private Map<Long, UserShortDto> getInitiatorsMap(List<Event> events) {
        Set<Long> ids = events.stream()
                .map(Event::getInitiatorId)
                .collect(Collectors.toSet());

        return userGateway.getUsersMap(ids).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> EventMapper.toUserShortDto(e.getValue())
                ));
    }

    // Инициатор одного события (null, если пользователь не найден)
    private UserShortDto getInitiator(Long initiatorId) {
        return EventMapper.toUserShortDto(
                userGateway.getUsersMap(List.of(initiatorId)).get(initiatorId));
    }

    // Применяет изменения из запроса пользователя к событию
    // null-поля пропускаются — означают "не менять"
    private void applyUserUpdates(Event event, UpdateEventUserRequest dto) {
        if (dto.getAnnotation() != null) event.setAnnotation(dto.getAnnotation());
        if (dto.getDescription() != null) event.setDescription(dto.getDescription());
        if (dto.getTitle() != null) event.setTitle(dto.getTitle());
        if (dto.getPaid() != null) event.setPaid(dto.getPaid());
        if (dto.getParticipantLimit() != null) event.setParticipantLimit(dto.getParticipantLimit());
        if (dto.getRequestModeration() != null) event.setRequestModeration(dto.getRequestModeration());
        if (dto.getLocation() != null) event.setLocation(LocationMapper.toEntity(dto.getLocation()));

        if (dto.getCategory() != null) {
            event.setCategory(getCategoryOrThrow(dto.getCategory()));
        }

        if (dto.getEventDate() != null) {
            validateEventDate(dto.getEventDate(), MIN_HOURS_BEFORE_EVENT_USER);
            event.setEventDate(dto.getEventDate());
        }

        // Изменение статуса через stateAction
        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case "SEND_TO_REVIEW" -> event.setState(EventState.PENDING);
                case "CANCEL_REVIEW" -> event.setState(EventState.CANCELED);
                default -> throw new IllegalArgumentException(
                        "Неизвестное действие для изменения статуса (stateAction): " + dto.getStateAction());
            }
        }
    }

    // Применяет изменения из запроса администратора к событию
    private void applyAdminUpdates(Event event, UpdateEventAdminRequest dto) {
        if (dto.getAnnotation() != null) event.setAnnotation(dto.getAnnotation());
        if (dto.getDescription() != null) event.setDescription(dto.getDescription());
        if (dto.getTitle() != null) event.setTitle(dto.getTitle());
        if (dto.getPaid() != null) event.setPaid(dto.getPaid());
        if (dto.getParticipantLimit() != null) event.setParticipantLimit(dto.getParticipantLimit());
        if (dto.getRequestModeration() != null) event.setRequestModeration(dto.getRequestModeration());
        if (dto.getLocation() != null) event.setLocation(LocationMapper.toEntity(dto.getLocation()));

        if (dto.getCategory() != null) {
            event.setCategory(getCategoryOrThrow(dto.getCategory()));
        }

        if (dto.getEventDate() != null) {
            validateEventDate(dto.getEventDate(), MIN_HOURS_BEFORE_EVENT_ADMIN);
            event.setEventDate(dto.getEventDate());
        }

        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case "PUBLISH_EVENT" -> {
                    // Публиковать можно только PENDING событие
                    if (!EventState.PENDING.equals(event.getState())) {
                        throw new ConflictException(
                                "Невозможно опубликовать событие, так как оно находится не в подходящем статусе: "
                                        + event.getState());
                    }
                    // Дата события не раньше чем через час после публикации
                    validateEventDate(event.getEventDate(), MIN_HOURS_BEFORE_EVENT_ADMIN);
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                }
                case "REJECT_EVENT" -> {
                    // Отклонять можно только не опубликованное событие
                    if (EventState.PUBLISHED.equals(event.getState())) {
                        throw new ConflictException(
                                "Невозможно отклонить событие, так как оно уже опубликовано");
                    }
                    event.setState(EventState.CANCELED);
                }
                default -> throw new IllegalArgumentException(
                        "Неизвестное действие для изменения статуса (stateAction): " + dto.getStateAction());
            }
        }
    }

    private void validateEventDate(LocalDateTime eventDate, int minHours) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(minHours))) {
            throw new IllegalArgumentException(
                    "Поле: eventDate. Error: должно содержать дату не ранее чем через "
                            + minHours + " час(а) от текущего момента. Value: " + eventDate);
        }
    }

    private Event getEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
    }

    private Category getCategoryOrThrow(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));
    }
}
