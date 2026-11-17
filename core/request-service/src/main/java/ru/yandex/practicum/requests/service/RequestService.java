package ru.yandex.practicum.requests.service;


import ru.yandex.practicum.requests.dto.EventRequestStatusUpdateRequest;
import ru.yandex.practicum.requests.dto.EventRequestStatusUpdateResult;
import ru.yandex.practicum.requests.dto.ParticipationRequestDto;

import java.util.List;
import java.util.Map;

public interface RequestService {

    // --- заявки пользователя ---
    List<ParticipationRequestDto> findById(Long userId);

    ParticipationRequestDto save(Long userId, Long eventId);

    ParticipationRequestDto cancel(Long userId, Long requestId);

    // --- владелец события ---
    List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId);

    EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId,
                                                       EventRequestStatusUpdateRequest dto);

    // --- внутренний API ---
    Map<Long, Long> getConfirmedCounts(List<Long> eventIds);

}
