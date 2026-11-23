package ru.yandex.practicum.aggregator.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.yandex.practicum.aggregator.service.SimilarityCalculator;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionListener {

    private final SimilarityCalculator calculator;
    private final SimilarityProducer producer;

    @KafkaListener(topics = "${aggregator.topic.user-actions}")
    public void onUserAction(UserActionAvro action) {
        log.debug("Получено действие: {}", action);
        calculator.update(action).forEach(producer::send);
    }
}
