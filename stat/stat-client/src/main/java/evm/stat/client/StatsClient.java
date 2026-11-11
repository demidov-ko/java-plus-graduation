package evm.stat.client;

import evm.stat.client.exceptions.StatsServerUnavailable;
import evm.stat.dto.HitDto;
import evm.stat.dto.ViewStatsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
public class StatsClient extends BaseClient {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DiscoveryClient discoveryClient;
    private final RetryTemplate retryTemplate;
    private final String statsServiceId;

    public StatsClient(DiscoveryClient discoveryClient,
                       RetryTemplate retryTemplate,
                       String statsServiceId) {
        super();
        this.discoveryClient = discoveryClient;
        this.retryTemplate = retryTemplate;
        this.statsServiceId = statsServiceId;
    }

    //записывает обращение к эндпоинту в сервис статистики
    public void hit(HitDto hitDto) {
        try {
            log.debug("Отправляем hit: app={}, uri={}, ip={}", hitDto.getApp(), hitDto.getUri(), hitDto.getIp());
            post(makeUri("/hit"), hitDto);
        } catch (Exception e) {
            log.warn("Не удалось отправить hit в сервис статистики: {}", e.getMessage());
        }
    }

    //возвращает статистику за указанный период
    public List<ViewStatsDto> getStats(LocalDateTime start,
                                       LocalDateTime end,
                                       List<String> uris,
                                       boolean unique) {
        String path = UriComponentsBuilder.fromPath("/stats")
                .queryParam("start", start.format(FORMATTER))
                .queryParam("end", end.format(FORMATTER))
                .queryParam("unique", unique)
                .queryParam("uris", uris != null ? uris : List.of())
                .build(false)
                .encode()
                .toUriString();
        return getList(makeUri(path), ViewStatsDto[].class);
    }

    //строит полный URI: базовый адрес из службы обнаружения + путь
    private URI makeUri(String path) {
        // получаем живой экземпляр stat-server, потом склеивает http://host:port с путём
        ServiceInstance instance = retryTemplate.execute(ctx -> getInstance());
        return URI.create("http://" + instance.getHost() + ":" + instance.getPort() + path);
    }

    private ServiceInstance getInstance() {
        try {
            return discoveryClient
                    .getInstances(statsServiceId)
                    .getFirst();
        } catch (Exception exception) {
            throw new StatsServerUnavailable(
                    "Ошибка обнаружения адреса сервиса статистики с id: " + statsServiceId,
                    exception
            );
        }
    }
}
