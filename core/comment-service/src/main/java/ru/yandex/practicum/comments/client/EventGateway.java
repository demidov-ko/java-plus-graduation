package ru.yandex.practicum.comments.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.common.event.EventClient;
import ru.yandex.practicum.common.event.EventInternalDto;
import ru.yandex.practicum.common.exceptions.NotFoundException;

@Component
@RequiredArgsConstructor
public class EventGateway {

    private final EventClient eventClient;

    @Retry(name = "event-service")
    @CircuitBreaker(name = "event-service")
    public EventInternalDto getEvent(Long eventId) {
        try {
            return eventClient.getEvent(eventId);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Событие с id = " + eventId + " не найдено!");
        }
    }
}
