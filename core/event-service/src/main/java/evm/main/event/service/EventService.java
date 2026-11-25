package evm.main.event.service;

import evm.main.event.dto.*;
import ru.yandex.practicum.common.event.EventInternalDto;

import java.time.LocalDateTime;
import java.util.List;

public interface EventService {

    // --- Public ---
    List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                        LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                        Boolean onlyAvailable, String sort,
                                        Integer from, Integer size);

    EventFullDto getPublicEventById(Long id, Long userId);

    // --- Private ---
    List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size);

    EventFullDto addEvent(Long userId, NewEventDto newEventDto);

    EventFullDto getUserEventById(Long userId, Long eventId);

    EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest request);

    // --- Admin ---
    List<EventFullDto> getAdminEvents(List<Long> users, List<String> states,
                                      List<Long> categories,
                                      LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                      Integer from, Integer size);

    EventFullDto updateAdminEvent(Long eventId, UpdateEventAdminRequest request);

    EventInternalDto getInternalEvent(Long eventId);

    List<EventShortDto> getRecommendations(Long userId, Integer maxResults);

    void likeEvent(Long userId, Long eventId);
}
