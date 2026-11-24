package evm.stat.client;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CollectorClient {

    @GrpcClient("collector")
    private UserActionControllerGrpc.UserActionControllerBlockingStub client;

    // Статистика второстепенна: при недоступности Collector основной запрос не должен падать
    public void sendUserAction(long userId, long eventId, ActionTypeProto actionType) {
        Instant now = Instant.now();
        UserActionProto request = UserActionProto.newBuilder()
                .setUserId(userId)
                .setEventId(eventId)
                .setActionType(actionType)
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build())
                .build();
        try {
            client.withDeadlineAfter(2, TimeUnit.SECONDS).collectUserAction(request);
            log.info("Действие отправлено в Collector: пользователь={}, мероприятие={}, тип={}",
                    userId, eventId, actionType);
        } catch (StatusRuntimeException e) {
            log.warn("Не удалось отправить действие {} (user={}, event={}) в Collector: {}",
                    actionType, userId, eventId, e.getStatus());
        }
    }
}
