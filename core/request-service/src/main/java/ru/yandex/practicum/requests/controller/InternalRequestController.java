package ru.yandex.practicum.requests.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.requests.service.RequestService;

import java.util.List;
import java.util.Map;

// Внутренний API для других сервисов (через Feign). В gateway не выставляется
@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
public class InternalRequestController {

    private final RequestService service;

    @GetMapping("/confirmed")
    public Map<Long, Long> getConfirmedCounts(@RequestParam(required = false) List<Long> eventIds) {
        return service.getConfirmedCounts(eventIds);
    }

    // есть ли у пользователя подтверждённая заявка на событие (для лайков в event-service)
    @GetMapping("/participation")
    public boolean isParticipant(@RequestParam Long userId, @RequestParam Long eventId) {
        return service.isParticipant(userId, eventId);
    }
}
