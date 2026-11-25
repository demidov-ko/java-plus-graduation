package ru.yandex.practicum.aggregator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SimilarityCalculator {

    private static final double VIEW_WEIGHT = 0.4;
    private static final double REGISTER_WEIGHT = 0.8;
    private static final double LIKE_WEIGHT = 1.0;

    // eventId - (userId -> максимальный вес действия пользователя с мероприятием)
    private final Map<Long, Map<Long, Double>> userWeights = new HashMap<>();
    // eventId - сумма весов всех пользователей (S_A)
    private final Map<Long, Double> eventSums = new HashMap<>();
    // меньший eventId - (больший eventId -> S_min)
    private final Map<Long, Map<Long, Double>> minSums = new HashMap<>();

    // Обрабатывает действие и возвращает пересчитанное сходство. Вызывается из одного потока
    public List<EventSimilarityAvro> update(UserActionAvro action) {
        long eventId = action.getEventId();
        long userId = action.getUserId();
        double newWeight = toWeight(action.getActionType());

        log.info("Событие: eventId={}, userId={}, actionType={}, вес={}",
                eventId, userId, action.getActionType(), newWeight);

        Map<Long, Double> users = userWeights.computeIfAbsent(eventId, k -> new HashMap<>());
        double oldWeight = users.getOrDefault(userId, 0.0);

        log.debug("Проверка весов");
        // максимальный вес не вырос, значит, сходство не меняется
        if (newWeight <= oldWeight) {
            log.debug("Без изменений: eventId={}, userId={}, старый_вес={}, новый_вес={}",
                    eventId, userId, oldWeight, newWeight);
            return List.of();
        }

        log.debug("{} < {}, пересчет запущен", oldWeight, newWeight);
        users.put(userId, newWeight);
        eventSums.merge(eventId, newWeight - oldWeight, Double::sum);

        log.debug("После обновления: eventSums={}, пользователь {} на мероприятии {} = {}",
                eventSums, userId, eventId, newWeight);

        // история этого пользователя: события, с которыми он уже взаимодействовал
        TreeSet<Long> userHistory = userWeights.entrySet().stream()
                .filter(e -> e.getValue().containsKey(userId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));

        // обновляем S_min с мероприятиями, с которыми взаимодействовал этот же пользователь
        for (long other : userHistory) {
            if (other == eventId) {
                continue;
            }
            double otherWeight = userWeights.get(other).get(userId);
            double oldMin = getMin(eventId, other);
            double delta = Math.min(newWeight, otherWeight) - Math.min(oldWeight, otherWeight);
            if (delta != 0) {
                log.debug("Пара ({},{}): oldMin={}, delta={}, новое значение = {}",
                        eventId, other, oldMin, delta, oldMin + delta);
                putMin(eventId, other, oldMin + delta);
            }
        }

        // пересчитываем сходство только с событиями из истории ЭТОГО пользователя
        List<EventSimilarityAvro> result = new ArrayList<>();
        double sqrtA = Math.sqrt(eventSums.get(eventId));
        for (long other : userHistory) {
            if (other == eventId) {
                continue;
            }
            double min = getMin(eventId, other);
            double score = min / (sqrtA * Math.sqrt(eventSums.get(other)));
            result.add(EventSimilarityAvro.newBuilder()
                    .setEventA(Math.min(eventId, other))
                    .setEventB(Math.max(eventId, other))
                    .setScore(score)
                    .setTimestamp(Instant.now())
                    .build());
        }

        log.info("Результат: {} похожих пар для eventId={}. Строки: [{}]",
                result.size(), eventId,
                result.stream()
                        .map(e -> String.format("%d,%d,%.10f", e.getEventA(), e.getEventB(), e.getScore()))
                        .collect(Collectors.joining(",")));

        return result;
    }

    private double toWeight(ActionTypeAvro type) {
        return switch (type) {
            case VIEW -> VIEW_WEIGHT;
            case REGISTER -> REGISTER_WEIGHT;
            case LIKE -> LIKE_WEIGHT;
            default -> 0.0;
        };
    }

    private void putMin(long eventA, long eventB, double sum) {
        long first = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);
        minSums.computeIfAbsent(first, k -> new HashMap<>()).put(second, sum);
    }

    private double getMin(long eventA, long eventB) {
        long first = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);
        Map<Long, Double> inner = minSums.get(first);
        return inner == null ? 0.0 : inner.getOrDefault(second, 0.0);
    }
}
