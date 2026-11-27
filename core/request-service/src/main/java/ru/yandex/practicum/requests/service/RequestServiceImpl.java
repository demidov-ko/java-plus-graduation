package ru.yandex.practicum.requests.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.yandex.practicum.common.event.EventInternalDto;
import ru.yandex.practicum.requests.client.EventGateway;
import ru.yandex.practicum.requests.client.UserGateway;
import ru.yandex.practicum.requests.dto.EventRequestStatusUpdateRequest;
import ru.yandex.practicum.requests.dto.EventRequestStatusUpdateResult;
import ru.yandex.practicum.requests.dto.ParticipationRequestDto;
import ru.yandex.practicum.common.exceptions.ConflictException;
import ru.yandex.practicum.common.exceptions.NotFoundException;
import ru.yandex.practicum.requests.mapper.RequestMapper;
import ru.yandex.practicum.requests.model.Request;
import ru.yandex.practicum.requests.model.Status;
import ru.yandex.practicum.requests.repository.RequestRepositoryJpa;
import evm.stat.client.CollectorClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class RequestServiceImpl implements RequestService {

    private final UserGateway userGateway;
    private final EventGateway eventGateway;
    private final RequestRepositoryJpa requestRepository;
    private final RequestMapper mapper;
    private final CollectorClient collectorClient;   // фиксация действий для рекомендаций

    @Override
    public List<ParticipationRequestDto> findById(Long userId) {

        // бросает NotFoundException, если пользователя нет
        userGateway.getUser(userId);

        List<Request> requestsList = requestRepository.findAllByUserId(userId);

        if (requestsList.isEmpty()) {
            return Collections.emptyList();
        }

        return requestsList.stream()
                .map(mapper::toDto)
                .toList();

    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    // HTTP/gRPC-вызовы не должны выполняться внутри транзакции БД
    public ParticipationRequestDto save(Long userId, Long eventId) {

        userGateway.getUser(userId);

        EventInternalDto event = eventGateway.getEvent(eventId);

        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException(
                    "Нельзя отправить повторную заявку на участие в мероприятии."
            );
        }

        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictException(
                    "Инициатор события не может добавить запрос на участие в своём событии."
            );
        }

        if (!"PUBLISHED".equals(event.getState())) {
            throw new ConflictException(
                    "Нельзя участвовать в неопубликованном событии."
            );
        }

        Status status;
        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            // модерация выключена или лимит = 0 --> подтверждаем автоматически
            status = Status.CONFIRMED;
        } else {
            status = Status.PENDING;
        }

        if (status == Status.CONFIRMED && event.getParticipantLimit() > 0) {
            long confirmedCount = requestRepository.countByEventIdAndStatus(
                    eventId,
                    Status.CONFIRMED
            );

            if (confirmedCount >= event.getParticipantLimit()) {
                throw new ConflictException(
                        "У события достигнут лимит запросов на участие"
                );
            }
        }

        Request request = mapper.mapToRequest(userId, eventId);
        request.setStatus(status);
        Request saved = requestRepository.save(request);

        // регистрация на мероприятие: фиксируем в рекомендательной системе
        collectorClient.sendUserAction(userId, eventId, ActionTypeProto.ACTION_REGISTER);

        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancel(Long userId, Long requestId) {

        userGateway.getUser(userId);

        Optional<Request> findRequest = requestRepository.findById(requestId);

        if (findRequest.isEmpty()) {
            throw new NotFoundException("Запроса с ID " + requestId + " не найдено");
        }

        if (!findRequest.get().getRequesterId().equals(userId)) {
            throw new ConflictException(
                    "Заявка с ID " + requestId + " не принадлежит пользователю " + userId
            );
        }

        findRequest.get().setStatus(Status.CANCELED);

        Request update = requestRepository.save(findRequest.get());

        return mapper.toDto(update);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        userGateway.getUser(userId);
        EventInternalDto event = eventGateway.getEvent(eventId);
        checkOwner(event, userId, eventId);

        return requestRepository.findAllByEventId(eventId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest dto) {
        userGateway.getUser(userId);
        EventInternalDto event = eventGateway.getEvent(eventId);

        // Проверка прав организатора
        checkOwner(event, userId, eventId);

        List<Long> requestedIds = dto.getRequestIds();

        // Получаем заявки только для этого события
        List<Request> requests = requestRepository.findAllByIdInAndEventId(dto.getRequestIds(), eventId);

        if (requests.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Обнаружены ID заявок, не принадлежащих данному событию. " +
                    "Проверьте корректность переданных requestIds.");
        }

        if (requests.isEmpty()) {
            throw new NotFoundException("Заявки не найдены для события " + eventId);
        }

        // Если модерация отключена или лимит = 0 - подтверждение не требуется
        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            // Автоматически подтверждаем все заявки
            requests.forEach(r -> r.setStatus(Status.CONFIRMED));
            requestRepository.saveAll(requests);

            return EventRequestStatusUpdateResult.builder()
                    .confirmedRequests(requests.stream()
                            .map(mapper::toDto)
                            .collect(Collectors.toList()))
                    .rejectedRequests(List.of())
                    .build();
        }

        Long confirmed = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);

        // Лимит уже достигнут - нельзя подтверждать новые заявки
        if (event.getParticipantLimit() > 0 && confirmed >= event.getParticipantLimit()) {
            throw new ConflictException("Лимит участников достигнут");
        }

        // Проверяем что все заявки в статусе PENDING
        requests.forEach(r -> {
            if (!Status.PENDING.equals(r.getStatus())) {
                throw new ConflictException("Заявка с id=" + r.getId() +
                        " должна находиться в статусе PENDING, текущий статус: " + r.getStatus()
                );
            }
        });

        Status newStatus = Status.valueOf(dto.getStatus());

        List<Request> confirmedList = List.of();
        List<Request> rejectedList = List.of();

        if (Status.CONFIRMED.equals(newStatus)) {
            // Подтверждаем заявки пока не достигнем лимита
            long available = event.getParticipantLimit() - confirmed;

            confirmedList = requests.stream()
                    .limit(available)
                    .peek(r -> r.setStatus(Status.CONFIRMED))
                    .toList();

            // Остальные автоматически отклоняем
            rejectedList = requests.stream()
                    .skip(available)
                    .peek(r -> r.setStatus(Status.REJECTED))
                    .collect(Collectors.toList());

            // Если лимит исчерпан - отклоняем все ожидающие заявки на это событие
            if (confirmedList.size() == available) {
                requestRepository.findAllByEventId(eventId).stream()
                        .filter(r -> Status.PENDING.equals(r.getStatus()))
                        .forEach(r -> r.setStatus(Status.REJECTED));
            }
        } else {
            rejectedList = requests.stream()
                    .peek(r -> r.setStatus(Status.REJECTED))
                    .collect(Collectors.toList());
        }

        requestRepository.saveAll(requests);

        List<Request> finalConfirmed = confirmedList;
        List<Request> finalRejected = rejectedList;

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(finalConfirmed.stream()
                        .map(mapper::toDto).collect(Collectors.toList()))
                .rejectedRequests(finalRejected.stream()
                        .map(mapper::toDto).collect(Collectors.toList()))
                .build();
    }

    @Override
    public boolean isParticipant(Long userId, Long eventId) {
        // участник = есть подтверждённая заявка
        return requestRepository.existsByEventIdAndRequesterIdAndStatus(eventId, userId, Status.CONFIRMED);
    }

    @Override
    public Map<Long, Long> getConfirmedCounts(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }
        return requestRepository.countConfirmedByEventIds(eventIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));
    }

    private void checkOwner(EventInternalDto event, Long userId, Long eventId) {
        if (!event.getInitiatorId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }
    }

}
