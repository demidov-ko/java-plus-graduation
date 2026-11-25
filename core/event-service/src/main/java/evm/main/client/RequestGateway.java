package evm.main.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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

    // eventId -> число подтверждённых заявок. Один запрос на весь список
    @Retry(name = "request-service", fallbackMethod = "confirmedMapFallback")
    @CircuitBreaker(name = "request-service")
    public Map<Long, Long> getConfirmedMap(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return requestClient.getConfirmedCounts(List.copyOf(eventIds));
    }

    // Не вызывает getConfirmedMap напрямую: вызов метода своего же класса идёт мимо прокси,
    // и аннотации Resilience4j не сработают, поэтому метод аннотирован отдельно
    @Retry(name = "request-service", fallbackMethod = "confirmedCountFallback")
    @CircuitBreaker(name = "request-service")
    public Long getConfirmedCount(Long eventId) {
        return requestClient.getConfirmedCounts(List.of(eventId)).getOrDefault(eventId, 0L);
    }

    // Без fallback: лайк нельзя проводить при недоступном request-service,
    // пользователь должен увидеть ошибку, а не ложное «не участник»
    @Retry(name = "request-service")
    @CircuitBreaker(name = "request-service")
    public boolean isParticipant(Long userId, Long eventId) {
        return requestClient.isParticipant(userId, eventId);
    }

    public Map<Long, Long> confirmedMapFallback(Collection<Long> eventIds, Throwable t) {
        log.warn("request-service недоступен ({}): confirmedRequests = 0", t.getClass().getSimpleName());
        return Map.of();
    }

    public Long confirmedCountFallback(Long eventId, Throwable t) {
        log.warn("request-service недоступен ({}): confirmedRequests = 0", t.getClass().getSimpleName());
        return 0L;
    }
}
