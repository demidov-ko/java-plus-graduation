package ru.yandex.practicum.comments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import ru.yandex.practicum.common.event.EventClient;
import ru.yandex.practicum.common.user.UserClient;

@SpringBootApplication
@EnableFeignClients(clients = {UserClient.class, EventClient.class})
public class CommentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommentServiceApplication.class, args);
    }
}