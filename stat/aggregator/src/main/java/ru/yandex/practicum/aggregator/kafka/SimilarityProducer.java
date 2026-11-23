package ru.yandex.practicum.aggregator.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimilarityProducer {

    private final KafkaTemplate<String, SpecificRecordBase> kafkaTemplate;

    @Value("${aggregator.topic.events-similarity}")
    private String topic;

    public void send(EventSimilarityAvro similarity) {
        kafkaTemplate.send(topic, similarity.getEventA() + "-" + similarity.getEventB(), similarity);
        log.info("Сходство мероприятий {} и {}: {}",
                similarity.getEventA(), similarity.getEventB(), similarity.getScore());
    }
}
