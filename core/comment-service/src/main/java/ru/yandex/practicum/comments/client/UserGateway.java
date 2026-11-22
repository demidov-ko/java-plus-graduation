package ru.yandex.practicum.comments.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.common.exceptions.NotFoundException;
import ru.yandex.practicum.common.user.UserClient;
import ru.yandex.practicum.common.user.UserDto;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
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
            throw new NotFoundException("Автор/пользователь с id = " + userId + " не найден!");
        }
    }

    @Retry(name = "user-service", fallbackMethod = "usersFallback")
    @CircuitBreaker(name = "user-service")
    public Map<Long, UserDto> getUsersMap(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userClient.getUsers(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(UserDto::getId, Function.identity()));
    }

    public Map<Long, UserDto> usersFallback(Collection<Long> ids, Throwable t) {
        log.warn("user-service недоступен ({}): имена авторов не загружены", t.getClass().getSimpleName());
        return Map.of();
    }
}
