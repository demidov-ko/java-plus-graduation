package ru.yandex.practicum.common;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-service", path = "/internal/users")
public interface UserClient {

    @GetMapping("/{userId}")
    UserDto getUser(@PathVariable("userId") Long userId);

    // один запрос на список вместо N запросов
    @GetMapping
    List<UserDto> getUsers(@RequestParam("ids") List<Long> ids);
}
