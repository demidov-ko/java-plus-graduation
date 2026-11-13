package ru.yandex.practicum.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.common.UserDto;
import ru.yandex.practicum.user.service.UserService;

import java.util.List;

// Внутренний API для других сервисов (через Feign). В gateway не выставляется
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserIternalController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public UserDto getUser(@PathVariable Long userId) {
        return userService.findById(userId);
    }

    @GetMapping
    public List<UserDto> getUsers(@RequestParam(required = false) List<Long> ids) {
        return userService.findUsersByIds(ids);
    }
}
