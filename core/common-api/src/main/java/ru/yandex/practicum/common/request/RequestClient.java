package ru.yandex.practicum.common.request;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "request-service", path = "/internal/requests")
public interface RequestClient {

    // eventId -> число подтверждённых заявок. События без таких заявок в ответе отсутствуют
    @GetMapping("/confirmed")
    Map<Long, Long> getConfirmedCounts(@RequestParam("eventIds") List<Long> eventIds);
}
