package ru.yandex.practicum.collector.controller;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;
import ru.yandex.practicum.collector.kafka.UserActionProducer;
import ru.yandex.practicum.collector.mapper.UserActionMapper;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserActionController extends UserActionControllerGrpc.UserActionControllerImplBase {

    private final UserActionProducer producer;

    @Override
    public void collectUserAction(UserActionProto request, StreamObserver<Empty> responseObserver) {
        try {
            producer.send(UserActionMapper.toAvro(request));
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Не удалось обработать действие пользователя: {}", e.getMessage(), e);
            responseObserver.onError(new io.grpc.StatusRuntimeException(
                    Status.INTERNAL.withDescription(e.getMessage()).withCause(e)));
        }
    }
}
