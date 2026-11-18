package evm.main.compilations.service;

import evm.main.client.RequestGateway;
import evm.main.client.UserGateway;
import evm.main.compilations.dto.CompilationDto;
import evm.main.compilations.dto.NewCompilationDto;
import evm.main.compilations.dto.UpdateCompilationRequest;
import evm.main.compilations.mapper.CompilationMapper;
import evm.main.compilations.model.Compilation;
import evm.main.compilations.repository.CompilationRepository;
import evm.main.event.dto.EventShortDto;
import evm.main.event.dto.UserShortDto;
import evm.main.event.mapper.EventMapper;
import evm.main.event.model.Event;
import evm.main.event.repository.EventRepository;
import ru.yandex.practicum.common.exceptions.NotFoundException;
import evm.stat.client.StatsClient;
import evm.stat.dto.ViewStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final RequestGateway requestGateway;
    private final StatsClient statsClient;
    private final UserGateway userGateway;

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto dto) {

        Set<Event> events = dto.getEvents() == null
                ? new HashSet<>()
                : new HashSet<>(eventRepository.findAllById(dto.getEvents()));

        Compilation compilation =
                CompilationMapper.toEntity(dto, events);

        Compilation saved = compilationRepository.save(compilation);

        return buildCompilationDto(saved);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {

        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Подборка с id=" + compId + " не найдена");
        }

        compilationRepository.deleteById(compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId,
                                            UpdateCompilationRequest dto) {

        Compilation compilation = getCompilationOrThrow(compId);

        if (dto.getTitle() != null) {
            compilation.setTitle(dto.getTitle());
        }

        if (dto.getPinned() != null) {
            compilation.setPinned(dto.getPinned());
        }

        if (dto.getEvents() != null) {
            compilation.setEvents(
                    new HashSet<>(eventRepository.findAllById(dto.getEvents()))
            );
        }

        Compilation saved = compilationRepository.save(compilation);

        return buildCompilationDto(saved);
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned,
                                                Integer from,
                                                Integer size) {

        Pageable pageable = PageRequest.of(from / size, size);

        List<Compilation> compilations;

        if (pinned == null) {
            compilations = compilationRepository.findAll(pageable).getContent();
        } else {
            compilations = compilationRepository.findAllByPinned(pinned, pageable);
        }

        return buildCompilationDtos(compilations);
    }

    @Override
    public CompilationDto getCompilation(Long compId) {

        return buildCompilationDto(getCompilationOrThrow(compId));
    }

    // ==========================================================
    // private
    // ==========================================================

    private Compilation getCompilationOrThrow(Long id) {
        return compilationRepository.findById(id)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Подборка с id=" + id + " не найдена"));
    }

    private CompilationDto buildCompilationDto(Compilation compilation) {
        return buildCompilationDtos(List.of(compilation)).get(0);
    }

    // Собирает DTO сразу для списка подборок: события всех подборок обрабатываются вместе,
    // поэтому на любой список уходит по одному запросу в статистику, в БД заявок и в user-service
    private List<CompilationDto> buildCompilationDtos(List<Compilation> compilations) {

        // Все события всех подборок без дублей (одно событие может быть в нескольких подборках)
        Map<Long, Event> eventsById = new HashMap<>();
        compilations.forEach(c ->
                c.getEvents().forEach(e -> eventsById.putIfAbsent(e.getId(), e)));
        List<Event> events = new ArrayList<>(eventsById.values());

        Map<Long, Long> viewsMap = getViewsMap(events);
        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(events);
        Map<Long, UserShortDto> initiatorsMap = getInitiatorsMap(events);

        Map<Long, EventShortDto> eventDtosById = events.stream()
                .collect(Collectors.toMap(
                        Event::getId,
                        event -> EventMapper.toShortDto(
                                event,
                                initiatorsMap.get(event.getInitiatorId()),
                                viewsMap.getOrDefault(event.getId(), 0L),
                                confirmedMap.getOrDefault(event.getId(), 0L))));

        return compilations.stream()
                .map(c -> CompilationMapper.toDto(
                        c,
                        c.getEvents().stream()
                                .map(e -> eventDtosById.get(e.getId()))
                                .toList()))
                .toList();
    }

    private Map<Long, Long> getViewsMap(List<Event> events) {

        if (events.isEmpty()) {
            return Map.of();
        }

        try {

            List<String> uris = events.stream()
                    .map(e -> "/events/" + e.getId())
                    .toList();

            LocalDateTime earliest = events.stream()
                    .map(Event::getCreatedOn)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now().minusYears(5));

            List<ViewStatsDto> stats =
                    statsClient.getStats(
                            earliest,
                            LocalDateTime.now(),
                            uris,
                            true);

            return stats.stream()
                    .collect(Collectors.toMap(
                            s -> Long.parseLong(
                                    s.getUri().replace("/events/", "")),
                            ViewStatsDto::getHits));

        } catch (Exception e) {

            log.warn("Не удалось получить статистику: {}",
                    e.getMessage());

            return Map.of();
        }
    }

    private Map<Long, Long> getConfirmedRequestsMap(List<Event> events) {
        return requestGateway.getConfirmedMap(
                events.stream().map(Event::getId).toList());
    }

    // Инициаторы всех событий одним запросом в user-service
    private Map<Long, UserShortDto> getInitiatorsMap(List<Event> events) {
        Set<Long> ids = events.stream()
                .map(Event::getInitiatorId)
                .collect(Collectors.toSet());

        return userGateway.getUsersMap(ids).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> EventMapper.toUserShortDto(e.getValue())));
    }
}
