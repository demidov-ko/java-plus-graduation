package evm.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import ru.yandex.practicum.common.request.RequestClient;
import ru.yandex.practicum.common.user.UserClient;

@SpringBootApplication
@EnableFeignClients(clients = {UserClient.class, RequestClient.class})
public class EventServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}
