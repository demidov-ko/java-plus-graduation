package evm.main.event.mapper;

import evm.main.category.mapper.CategoryMapper;
import evm.main.category.model.Category;
import evm.main.event.dto.EventFullDto;
import evm.main.event.dto.EventShortDto;
import evm.main.event.dto.NewEventDto;
import evm.main.event.dto.UserShortDto;
import evm.main.event.model.Event;
import evm.main.event.model.EventState;
import ru.yandex.practicum.common.UserDto;

import java.time.LocalDateTime;

public class EventMapper {

    private EventMapper() {
    }

    public static Event toEntity(NewEventDto dto, Category category, Long initiatorId) {
        return Event.builder()
                .annotation(dto.getAnnotation())
                .category(category)
                .description(dto.getDescription())
                .eventDate(dto.getEventDate())
                .initiatorId(initiatorId)
                .location(LocationMapper.toEntity(dto.getLocation()))
                .paid(dto.getPaid())
                .participantLimit(dto.getParticipantLimit())
                .requestModeration(dto.getRequestModeration())
                .title(dto.getTitle())
                .state(EventState.PENDING)
                .createdOn(LocalDateTime.now())
                .build();
    }

    // Краткая информация о событии
    public static EventShortDto toShortDto(Event event, UserShortDto initiator,
                                           Long views, Long confirmedRequests) {
        return EventShortDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(CategoryMapper.toDto(event.getCategory()))
                .confirmedRequests(confirmedRequests)
                .eventDate(event.getEventDate())
                .initiator(initiator)
                .paid(event.getPaid())
                .title(event.getTitle())
                .views(views)
                .build();
    }

    // Полная информация о событии
    public static EventFullDto toFullDto(Event event, UserShortDto initiator,
                                         Long views, Long confirmedRequests) {
        return EventFullDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(CategoryMapper.toDto(event.getCategory()))
                .confirmedRequests(confirmedRequests)
                .createdOn(event.getCreatedOn())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .initiator(initiator)
                .location(LocationMapper.toDto(event.getLocation()))
                .paid(event.getPaid())
                .participantLimit(event.getParticipantLimit())
                .publishedOn(event.getPublishedOn())
                .requestModeration(event.getRequestModeration())
                .state(event.getState())
                .title(event.getTitle())
                .views(views)
                .build();
    }

    public static UserShortDto toUserShortDto(UserDto user) {
        if (user == null) {
            return null;
        }
        return UserShortDto.builder()
                .id(user.getId())
                .name(user.getName())
                .build();
    }
}