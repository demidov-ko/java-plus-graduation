package ru.yandex.practicum.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.user.model.User;

import java.util.List;

public interface UserRepositoryJpa extends JpaRepository<User, Long> {

    @Override
    List<User> findAllById(Iterable<Long> id);

    Page<User> findAll(Pageable pageable);

    boolean existsByEmail(String email);
}