package ru.yandex.practicum.requests;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import ru.yandex.practicum.common.event.EventClient;
import ru.yandex.practicum.common.user.UserClient;

@SpringBootApplication
@EnableFeignClients(clients = {UserClient.class, EventClient.class})
public class RequestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RequestServiceApplication.class, args);
    }
}
