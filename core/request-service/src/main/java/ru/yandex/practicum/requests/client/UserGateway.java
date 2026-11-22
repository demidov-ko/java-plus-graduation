package ru.yandex.practicum.requests.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.common.user.UserClient;
import ru.yandex.practicum.common.user.UserDto;
import ru.yandex.practicum.common.exceptions.NotFoundException;

@Component
@RequiredArgsConstructor
public class UserGateway {

    private final UserClient userClient;

    @Retry(name = "user-service")
    @CircuitBreaker(name = "user-service")
    public UserDto getUser(Long userId) {
        try {
            return userClient.getUser(userId);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Пользователь с ID " + userId + " не найден");
        }
    }
}
