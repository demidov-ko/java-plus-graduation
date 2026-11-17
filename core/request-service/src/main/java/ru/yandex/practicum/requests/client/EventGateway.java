package ru.yandex.practicum.requests.client;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.common.event.EventClient;
import ru.yandex.practicum.common.event.EventInternalDto;
import ru.yandex.practicum.requests.exceptions.NotFoundException;

@Component
@RequiredArgsConstructor
public class EventGateway {

    private final EventClient eventClient;

    public EventInternalDto getEvent(Long eventId) {
        try {
            return eventClient.getEvent(eventId);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Мероприятия с ID " + eventId + " не найдено");
        }
    }
}
