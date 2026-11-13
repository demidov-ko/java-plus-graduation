package ru.yandex.practicum.user.mapper;

import org.springframework.stereotype.Component;
import ru.yandex.practicum.common.UserDto;
import ru.yandex.practicum.user.dto.NewRequestUserDto;
import ru.yandex.practicum.user.model.User;

@Component
public class UserMapper {

    public User mapToUser(NewRequestUserDto newUserDto) {
        User user = new User();
        user.setEmail(newUserDto.getEmail());
        user.setName(newUserDto.getName());

        return user;
    }

    public UserDto mapToUserDto(User user) {
        if (user == null) {
            return null;
        }

        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());

        return dto;
    }
}
