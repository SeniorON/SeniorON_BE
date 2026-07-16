package com.example.senioron.domain.home.dto.response;

import com.example.senioron.domain.home.entity.ActionType;
import com.example.senioron.domain.home.entity.FontSize;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SeniorHomeResponse {

    @JsonProperty("font_size")
    private FontSize fontSize;

    @JsonProperty("today_schedules")
    private List<TodayScheduleResponse> todaySchedules;

    private List<ButtonResponse> buttons;

    @Getter
    @AllArgsConstructor
    public static class ButtonResponse {

        @JsonProperty("button_id")
        private Long buttonId;

        @JsonProperty("button_order")
        private Integer buttonOrder;

        @JsonProperty("button_name")
        private String buttonName;

        private String icon;

        @JsonProperty("action_type")
        private ActionType actionType;

        @JsonProperty("action_value")
        private String actionValue;
    }
}