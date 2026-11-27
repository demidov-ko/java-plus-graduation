package ru.yandex.practicum.analyzer.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;
import ru.yandex.practicum.analyzer.service.RecommendationService;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class RecommendationsController extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {

    private final RecommendationService service;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> observer) {
        log.info("gRPC GetRecommendationsForUser: пользователь={}, максимум результатов={}",
                request.getUserId(), request.getMaxResults());
        respond(observer, () -> service.getRecommendationsForUser(request.getUserId(), request.getMaxResults()));
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request,
                                 StreamObserver<RecommendedEventProto> observer) {
        log.info("gRPC GetSimilarEvents: мероприятие={}, пользователь={}, максимум результатов={}",
                request.getEventId(), request.getUserId(), request.getMaxResults());
        respond(observer, () -> service.getSimilarEvents(
                request.getEventId(), request.getUserId(), request.getMaxResults()));
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> observer) {
        log.info("gRPC GetInteractionsCount: количество мероприятий={}", request.getEventIdCount());
        respond(observer, () -> service.getInteractionsCount(request.getEventIdList()));
    }

    private void respond(StreamObserver<RecommendedEventProto> observer,
                         Supplier<List<RecommendedEventProto>> supplier) {
        try {
            List<RecommendedEventProto> result = supplier.get();
            log.info("gRPC ответ: отправлено {} записей", result.size());
            result.forEach(observer::onNext);
            observer.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка gRPC-запроса: {}", e.getMessage(), e);
            observer.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
        }
    }
}
