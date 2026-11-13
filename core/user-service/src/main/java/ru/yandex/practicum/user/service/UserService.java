package ru.yandex.practicum.user.service;

import ru.yandex.practicum.common.UserDto;
import ru.yandex.practicum.user.dto.NewRequestUserDto;

import java.util.List;

public interface UserService {
    UserDto save(NewRequestUserDto newUserDto);

    UserDto findById(Long userId);

    List<UserDto> findUsersByIds(List<Long> ids);

    List<UserDto> findUsersByParam(Integer from, Integer size);

    void delete(Long userId);
}
