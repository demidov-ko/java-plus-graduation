package evm.stat.client;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public class AnalyzerClient {

    @GrpcClient("analyzer")
    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub client;

    public List<RecommendedEventProto> getRecommendationsForUser(long userId, int maxResults) {
        log.debug("Запрос рекомендаций в Analyzer: пользователь={}, максимум={}", userId, maxResults);
        UserPredictionsRequestProto request = UserPredictionsRequestProto.newBuilder()
                .setUserId(userId)
                .setMaxResults(maxResults)
                .build();
        return read("рекомендации пользователя " + userId,
                () -> stub().getRecommendationsForUser(request));
    }

    public List<RecommendedEventProto> getSimilarEvents(long eventId, long userId, int maxResults) {
        log.debug("Запрос похожих мероприятий в Analyzer: мероприятие={}, пользователь={}, максимум={}",
                eventId, userId, maxResults);
        SimilarEventsRequestProto request = SimilarEventsRequestProto.newBuilder()
                .setEventId(eventId)
                .setUserId(userId)
                .setMaxResults(maxResults)
                .build();
        return read("похожие на мероприятие " + eventId, () -> stub().getSimilarEvents(request));
    }

    public List<RecommendedEventProto> getInteractionsCount(Collection<Long> eventIds) {
        log.debug("Запрос рейтинга в Analyzer: мероприятий={}", eventIds.size());
        InteractionsCountRequestProto request = InteractionsCountRequestProto.newBuilder()
                .addAllEventId(eventIds)
                .build();
        return read("рейтинг мероприятий " + eventIds, () -> stub().getInteractionsCount(request));
    }

    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub stub() {
        return client.withDeadlineAfter(3, TimeUnit.SECONDS);
    }

    // Поток читается целиком внутри try: ошибка gRPC может случиться и во время итерации.
    // При недоступности Analyzer возвращаем пустой список
    private List<RecommendedEventProto> read(String what, Supplier<Iterator<RecommendedEventProto>> call) {
        List<RecommendedEventProto> result = new ArrayList<>();
        try {
            call.get().forEachRemaining(result::add);
            log.debug("Analyzer вернул {} записей ({})", result.size(), what);
        } catch (StatusRuntimeException e) {
            log.warn("Analyzer недоступен ({}): {}", what, e.getStatus());
            return List.of();
        }
        return result;
    }
}
