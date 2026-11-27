package ru.yandex.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.yandex.practicum.analyzer.model.EventSimilarity;
import ru.yandex.practicum.analyzer.model.UserInteraction;
import ru.yandex.practicum.analyzer.repository.EventSimilarityRepository;
import ru.yandex.practicum.analyzer.repository.UserInteractionRepository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    // сколько ближайших соседей учитываем при предсказании оценки
    @Value("${analyzer.recommendations.neighbors:5}")
    private int neighbors;

    private final UserInteractionRepository interactionRepository;
    private final EventSimilarityRepository similarityRepository;

    private record Scored(long eventId, double score) {
    }

    // Мероприятия, похожие на указанное, с которыми пользователь ещё не взаимодействовал
    public List<RecommendedEventProto> getSimilarEvents(long eventId, long userId, int maxResults) {
        log.info("Запрос похожих мероприятий: мероприятие={}, пользователь={}, максимум результатов={}",
                eventId, userId, maxResults);

        Set<Long> seen = interactionRepository.findAllByUserId(userId).stream()
                .map(UserInteraction::getEventId)
                .collect(Collectors.toSet());

        // пустая коллекция в NOT IN может дать невалидный SQL,
        // если просмотренных мероприятий нет, подставляем список из одного элемента id,
        // который заведомо не существует в таблице
        Collection<Long> excluded = seen.isEmpty() ? List.of(-1L) : seen;

        List<RecommendedEventProto> result = similarityRepository
                .findTopSimilar(eventId, excluded, PageRequest.of(0, maxResults)).stream()
                .map(s -> new Scored(s.getEventA().equals(eventId) ? s.getEventB() : s.getEventA(), s.getScore()))
                .map(this::toProto)
                .toList();

        log.info("Найдено {} похожих мероприятий для eventId={} (у пользователя просмотрено {} мероприятий)",
                result.size(), eventId, seen.size());
        return result;
    }


    // Рекомендации для пользователя на основе предсказанной оценки
    public List<RecommendedEventProto> getRecommendationsForUser(long userId, int maxResults) {
        log.info("Запрос рекомендаций для пользователя={}, максимум результатов={}", userId, maxResults);

        // оценки пользователя: eventId - максимальный вес
        Map<Long, Double> ratings = interactionRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(UserInteraction::getEventId, UserInteraction::getWeight));
        if (ratings.isEmpty()) {
            log.info("У пользователя {} нет истории взаимодействий - рекомендации не строятся", userId);
            return List.of();
        }
        log.debug("Оценки пользователя {}: {}", userId, ratings);

        // этап 1: новые мероприятия, похожие на недавно просмотренные
        Set<Long> recent = interactionRepository
                .findAllByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, maxResults)).stream()
                .map(UserInteraction::getEventId)
                .collect(Collectors.toSet());

        Map<Long, Double> candidateScores = new HashMap<>();
        for (EventSimilarity s : similarityRepository.findAllByEvents(recent)) {
            long other = recent.contains(s.getEventA()) ? s.getEventB() : s.getEventA();
            if (!ratings.containsKey(other)) {
                candidateScores.merge(other, s.getScore(), Math::max);
            }
        }
        log.debug("Этап 1: недавние мероприятия={}, оценки кандидатов={}", recent, candidateScores);

        Set<Long> candidates = candidateScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (candidates.isEmpty()) {
            log.info("Для пользователя {} кандидатов не найдено", userId);
            return List.of();
        }
        log.debug("Этап 1: выбрано кандидатов={}, список={}", candidates.size(), candidates);

        // этап 2: для каждого кандидата ищем просмотренные мероприятия-соседи (одним запросом)
        Map<Long, List<Scored>> neighbors = new HashMap<>();
        for (EventSimilarity s : similarityRepository.findAllByEvents(candidates)) {
            long a = s.getEventA();
            long b = s.getEventB();
            if (candidates.contains(a) && ratings.containsKey(b)) {
                neighbors.computeIfAbsent(a, k -> new ArrayList<>()).add(new Scored(b, s.getScore()));
            }
            if (candidates.contains(b) && ratings.containsKey(a)) {
                neighbors.computeIfAbsent(b, k -> new ArrayList<>()).add(new Scored(a, s.getScore()));
            }
        }

        log.debug("Этап 2: у {} кандидатов есть просмотренные соседи", neighbors.size());

        List<RecommendedEventProto> result = candidates.stream()
                .map(candidate -> new Scored(candidate, predict(neighbors.getOrDefault(candidate, List.of()), ratings)))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(maxResults)
                .map(this::toProto)
                .toList();

        result.forEach(r -> log.info("Рекомендация для пользователя {}: eventId={}, score={}",
                userId, r.getEventId(), r.getScore()));
        return result;
    }

    // Сумма максимальных весов взаимодействий для каждого мероприятия
    public List<RecommendedEventProto> getInteractionsCount(Collection<Long> eventIds) {
        log.info("Запрос суммы взаимодействий для {} мероприятий", eventIds.size());

        if (eventIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> sums = interactionRepository.sumWeightsByEventIds(eventIds).stream()
                .collect(Collectors.toMap(
                        UserInteractionRepository.EventWeightSum::getEventId,
                        UserInteractionRepository.EventWeightSum::getTotal));

        List<RecommendedEventProto> result = eventIds.stream()
                .distinct()
                .map(id -> new Scored(id, sums.getOrDefault(id, 0.0)))
                .map(this::toProto)
                .toList();

        result.forEach(r -> log.info("  → eventId={}, сумма взаимодействий={}", r.getEventId(), r.getScore()));
        return result;
    }

    // оценка = сумма(оценка соседа * сходство) / сумма(сходство) по K ближайшим соседям
    private double predict(List<Scored> candidateNeighbors, Map<Long, Double> ratings) {
        List<Scored> nearest = candidateNeighbors.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(neighbors)
                .toList();

        log.debug("Предсказание по соседям: {}", nearest);

        double weighted = 0;
        double similaritySum = 0;
        for (Scored n : nearest) {
            weighted += ratings.get(n.eventId()) * n.score();
            similaritySum += n.score();
        }
        double prediction = similaritySum == 0 ? 0 : weighted / similaritySum;
        log.debug("Предсказание: взвешенная сумма={}, сумма сходств={}, итог={}", weighted, similaritySum, prediction);
        return prediction;
    }

    private RecommendedEventProto toProto(Scored scored) {
        return RecommendedEventProto.newBuilder()
                .setEventId(scored.eventId())
                .setScore(scored.score())
                .build();
    }
}
