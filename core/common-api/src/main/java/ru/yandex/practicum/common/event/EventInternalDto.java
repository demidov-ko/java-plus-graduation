package ru.yandex.practicum.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class  EventInternalDto {
    private Long id;
    private Long initiatorId;
    private String state;               // PENDING / PUBLISHED / CANCELED
    private Integer participantLimit;
    private Boolean requestModeration;
}
