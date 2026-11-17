package evm.main.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.common.request.RequestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestGateway {

    private final RequestClient requestClient;

    // eventId -> число подтверждённых заявок. Один запрос на весь список.
    // Если request-service недоступен, возвращаем пустую карту (у всех событий будет 0)
    public Map<Long, Long> getConfirmedMap(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        try {
            return requestClient.getConfirmedCounts(List.copyOf(eventIds));
        } catch (Exception e) {
            log.warn("Не удалось получить заявки из request-service: {}", e.getMessage());
            return Map.of();
        }
    }

    public Long getConfirmedCount(Long eventId) {
        return getConfirmedMap(List.of(eventId)).getOrDefault(eventId, 0L);
    }
}
