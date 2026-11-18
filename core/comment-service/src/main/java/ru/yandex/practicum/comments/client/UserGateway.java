package ru.yandex.practicum.comments.client;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.common.exceptions.NotFoundException;
import ru.yandex.practicum.common.user.UserClient;
import ru.yandex.practicum.common.user.UserDto;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserGateway {

    private final UserClient userClient;

    public UserDto getUser(Long userId) {
        try {
            return userClient.getUser(userId);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Автор/пользователь с id = " + userId + " не найден!");
        }
    }

    // Один запрос на весь список. Удалённые пользователи в карту не попадают
    public Map<Long, UserDto> getUsersMap(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userClient.getUsers(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(UserDto::getId, Function.identity()));
    }
}