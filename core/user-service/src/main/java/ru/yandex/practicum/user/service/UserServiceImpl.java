package ru.yandex.practicum.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.common.UserDto;
import ru.yandex.practicum.exceptions.ConflictException;
import ru.yandex.practicum.exceptions.NotFoundException;
import ru.yandex.practicum.user.dto.NewRequestUserDto;
import ru.yandex.practicum.user.mapper.UserMapper;
import ru.yandex.practicum.user.model.User;
import ru.yandex.practicum.user.repository.UserRepositoryJpa;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepositoryJpa userRepositoryJpa;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserDto save(NewRequestUserDto newUserDto) {

        boolean checkUser = userRepositoryJpa.existsByEmail(newUserDto.getEmail());

        if (checkUser) {
            throw new ConflictException("Пользователь с email " + newUserDto.getEmail() + " уже создан.");
        }

        User user = userRepositoryJpa.save(userMapper.mapToUser(newUserDto));
        return userMapper.mapToUserDto(user);
    }

    @Override
    public UserDto findById(Long userId) {
        return userRepositoryJpa.findById(userId)
                .map(userMapper::mapToUserDto)
                .orElseThrow(() -> new NotFoundException("Пользователь с id " + userId + " не найден."));
    }

    @Override
    public List<UserDto> findUsersByIds(List<Long> ids) {

        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        List<User> users = userRepositoryJpa.findAllById(ids);

        return users.stream()
                .map(userMapper::mapToUserDto)
                .toList();
    }

    @Override
    public List<UserDto> findUsersByParam(Integer from, Integer size) {

        Pageable pageable = PageRequest.of(from / size, size);

        return userRepositoryJpa.findAll(pageable)
                .map(userMapper::mapToUserDto)
                .getContent();

    }

    @Override
    @Transactional
    public void delete(Long userId) {

        boolean checkUser = userRepositoryJpa.existsById(userId);

        if (!checkUser) {
            throw new NotFoundException("Пользователь с id " + userId + " не найден.");
        }

        userRepositoryJpa.deleteById(userId);
    }
}
