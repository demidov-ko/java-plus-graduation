package ru.yandex.practicum.collector.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.UserActionAvro;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionProducer {

    private final KafkaTemplate<Long, SpecificRecordBase> kafkaTemplate;

    @Value("${collector.topic.user-actions}")
    private String topic;

    public void send(UserActionAvro action) {
        kafkaTemplate.send(topic, action.getUserId(), action);
        log.info("В топик {} отправлено действие: {}", topic, action);
    }
}
