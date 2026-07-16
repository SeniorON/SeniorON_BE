package com.example.senioron.domain.home.dto.response;

import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TodayScheduleResponse {

    private Long scheduleId;
    private ScheduleType scheduleType;
    private String title;
    private String description;
    private LocalTime scheduledTime;
}