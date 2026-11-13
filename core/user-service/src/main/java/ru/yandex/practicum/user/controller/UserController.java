package ru.yandex.practicum.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.common.UserDto;
import ru.yandex.practicum.user.dto.NewRequestUserDto;
import ru.yandex.practicum.user.service.UserService;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<UserDto> findUsers(
            @RequestParam(value = "ids", required = false) Long[] ids,
            @RequestParam(value = "from", defaultValue = "0") @PositiveOrZero Integer from,
            @RequestParam(value = "size", defaultValue = "10") @Positive Integer size) {
        if (ids == null || ids.length == 0) {
            return userService.findUsersByParam(from, size);
        }
        return userService.findUsersByIds(Arrays.asList(ids));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto save(
            @Valid @RequestBody NewRequestUserDto newRequestUserDto) {
        return userService.save(newRequestUserDto);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long userId) {
        userService.delete(userId);
    }
}
