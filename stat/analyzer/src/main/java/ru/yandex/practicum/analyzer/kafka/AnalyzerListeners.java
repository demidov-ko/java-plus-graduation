package ru.yandex.practicum.analyzer.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.yandex.practicum.analyzer.service.UpdateService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzerListeners {

    private final UpdateService updateService;

    @KafkaListener(
            topics = "${analyzer.topic.user-actions}",
            properties = "value.deserializer:ru.practicum.ewm.stats.serialization.UserActionDeserializer")
    public void onUserAction(UserActionAvro action) {
        log.info("Получено действие из Kafka: пользователь={}, мероприятие={}, тип={}, время={}",
                action.getUserId(), action.getEventId(), action.getActionType(), action.getTimestamp());
        updateService.saveUserAction(action);
    }

    @KafkaListener(
            topics = "${analyzer.topic.events-similarity}",
            properties = "value.deserializer:ru.practicum.ewm.stats.serialization.EventSimilarityDeserializer")
    public void onSimilarity(EventSimilarityAvro similarity) {
        log.info("Получено сходство из Kafka: пара мероприятий ({}, {}), score={}",
                similarity.getEventA(), similarity.getEventB(), similarity.getScore());
        updateService.saveSimilarity(similarity);
    }
}
