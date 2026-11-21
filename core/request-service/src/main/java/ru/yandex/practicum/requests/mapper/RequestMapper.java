package ru.yandex.practicum.requests.mapper;

import org.springframework.stereotype.Component;
import ru.yandex.practicum.requests.dto.ParticipationRequestDto;
import ru.yandex.practicum.requests.model.Request;
import ru.yandex.practicum.requests.model.Status;

import java.time.LocalDateTime;

@Component
public class RequestMapper {

    public Request mapToRequest(Long requesterId, Long eventId) {
        Request request = new Request();
        request.setCreated(LocalDateTime.now());
        request.setStatus(Status.PENDING);
        request.setRequesterId(requesterId);
        request.setEventId(eventId);

        return request;

    }

    public ParticipationRequestDto toDto(Request request) {
        return ParticipationRequestDto.builder()
                .id(request.getId())
                .event(request.getEventId())
                .requester(request.getRequesterId())
                .created(request.getCreated())
                .status(request.getStatus().name())
                .build();
    }
}

