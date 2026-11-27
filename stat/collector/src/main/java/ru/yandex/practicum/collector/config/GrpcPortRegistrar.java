package ru.yandex.practicum.collector.config;

import com.netflix.appinfo.ApplicationInfoManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.event.GrpcServerStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcPortRegistrar {

    private final ApplicationInfoManager applicationInfoManager;

    @EventListener
    public void onGrpcServerStarted(GrpcServerStartedEvent event) {
        String port = String.valueOf(event.getServer().getPort());
        applicationInfoManager.registerAppMetadata(Map.of("gRPC_port", port));
        log.info("gRPC-порт {} записан в metadata Eureka", port);
    }
}
