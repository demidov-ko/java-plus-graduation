package ru.yandex.practicum.requests.client;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.common.user.UserClient;
import ru.yandex.practicum.common.user.UserDto;
import ru.yandex.practicum.common.exceptions.NotFoundException;

@Component
@RequiredArgsConstructor
public class UserGateway {

    private final UserClient userClient;

    public UserDto getUser(Long userId) {
        try {
            return userClient.getUser(userId);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Пользователь с ID " + userId + " не найден");
        }
    }
}
