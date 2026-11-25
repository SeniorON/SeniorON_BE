package com.example.senioron.domain.home.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TodayScheduleResponse {

    @JsonProperty("schedule_count")
    private Integer scheduleCount;

    @JsonProperty("display_type")
    private ScheduleDisplayType displayType;

    @JsonProperty("schedule_id")
    private Long scheduleId;

    private String title;
    private String description;

    @JsonProperty("scheduled_time")
    private LocalTime scheduledTime;

    private TodayScheduleResponse(
            Integer scheduleCount,
            ScheduleDisplayType displayType,
            Long scheduleId,
            String title,
            String description,
            LocalTime scheduledTime
    ) {
        this.scheduleCount = scheduleCount;
        this.displayType = displayType;
        this.scheduleId = scheduleId;
        this.title = title;
        this.description = description;
        this.scheduledTime = scheduledTime;
    }

    public static TodayScheduleResponse empty() {
        return new TodayScheduleResponse(
                0,
                ScheduleDisplayType.NONE,
                null,
                null,
                null,
                null
        );
    }

    public static TodayScheduleResponse detail(
            Long scheduleId,
            String title,
            String description,
            LocalTime scheduledTime
    ) {
        return new TodayScheduleResponse(
                1,
                ScheduleDisplayType.DETAIL,
                scheduleId,
                title,
                description,
                scheduledTime
        );
    }

    public static TodayScheduleResponse count(
            Integer scheduleCount
    ) {
        return new TodayScheduleResponse(
                scheduleCount,
                ScheduleDisplayType.COUNT,
                null,
                null,
                null,
                null
        );
    }

    public Integer getScheduleCount() {
        return scheduleCount;
    }

    public ScheduleDisplayType getDisplayType() {
        return displayType;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalTime getScheduledTime() {
        return scheduledTime;
    }
}