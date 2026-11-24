package ru.yandex.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.yandex.practicum.analyzer.model.EventSimilarity;
import ru.yandex.practicum.analyzer.model.UserInteraction;
import ru.yandex.practicum.analyzer.repository.EventSimilarityRepository;
import ru.yandex.practicum.analyzer.repository.UserInteractionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
// запись в БД
public class UpdateService {

    private final UserInteractionRepository interactionRepository;
    private final EventSimilarityRepository similarityRepository;

    @Transactional
    public void saveUserAction(UserActionAvro action) {
        double weight = toWeight(action.getActionType());

        UserInteraction interaction = interactionRepository
                .findByUserIdAndEventId(action.getUserId(), action.getEventId())
                .orElseGet(() -> UserInteraction.builder()
                        .userId(action.getUserId())
                        .eventId(action.getEventId())
                        .weight(0.0)
                        .updatedAt(action.getTimestamp())
                        .build());

        if (weight > interaction.getWeight()) {
            log.debug("Обновление веса: пользователь {} для мероприятия {}: старый вес={}, новый вес={}",
                    action.getUserId(), action.getEventId(), interaction.getWeight(), weight);
            interaction.setWeight(weight);
        } else {
            log.debug("Вес не увеличен: текущий={}, предложенный={}", interaction.getWeight(), weight);
        }
        if (action.getTimestamp().isAfter(interaction.getUpdatedAt())) {
            interaction.setUpdatedAt(action.getTimestamp());
            log.debug("Обновлено время последнего взаимодействия для пары ({}, {}) на {}",
                    interaction.getUserId(), interaction.getEventId(), interaction.getUpdatedAt());
        }
        interactionRepository.save(interaction);
        log.info("Взаимодействие сохранено: пользователь={}, мероприятие={}, вес={}, время={}",
                interaction.getUserId(), interaction.getEventId(), interaction.getWeight(), interaction.getUpdatedAt());
    }

    @Transactional
    public void saveSimilarity(EventSimilarityAvro similarity) {
        EventSimilarity entity = similarityRepository
                .findByEventAAndEventB(similarity.getEventA(), similarity.getEventB())
                .orElseGet(() -> EventSimilarity.builder()
                        .eventA(similarity.getEventA())
                        .eventB(similarity.getEventB())
                        .build());

        entity.setScore(similarity.getScore());
        entity.setUpdatedAt(similarity.getTimestamp());
        similarityRepository.save(entity);

        log.info("Сходство сохранено: пара мероприятий ({}, {}), score={}, время={}",
                entity.getEventA(), entity.getEventB(), entity.getScore(), entity.getUpdatedAt());
    }

    private double toWeight(ActionTypeAvro type) {
        return switch (type) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }
}
