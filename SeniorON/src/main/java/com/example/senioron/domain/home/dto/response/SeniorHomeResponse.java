package com.example.senioron.domain.home.dto.response;

import com.example.senioron.domain.home.entity.ActionType;
import com.example.senioron.domain.home.entity.FontSize;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SeniorHomeResponse {

    @JsonProperty("font_size")
    private FontSize fontSize;

    @JsonProperty("today_schedule")
    private TodayScheduleResponse todaySchedule;

    private List<ButtonResponse> buttons;

    public SeniorHomeResponse(
            FontSize fontSize,
            TodayScheduleResponse todaySchedule,
            List<ButtonResponse> buttons
    ) {
        this.fontSize = fontSize;
        this.todaySchedule = todaySchedule;
        this.buttons = buttons;
    }

    public FontSize getFontSize() {
        return fontSize;
    }

    public TodayScheduleResponse getTodaySchedule() {
        return todaySchedule;
    }

    public List<ButtonResponse> getButtons() {
        return buttons;
    }

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

        public ButtonResponse(
                Long buttonId,
                Integer buttonOrder,
                String buttonName,
                String icon,
                ActionType actionType,
                String actionValue
        ) {
            this.buttonId = buttonId;
            this.buttonOrder = buttonOrder;
            this.buttonName = buttonName;
            this.icon = icon;
            this.actionType = actionType;
            this.actionValue = actionValue;
        }

        public Long getButtonId() {
            return buttonId;
        }

        public Integer getButtonOrder() {
            return buttonOrder;
        }

        public String getButtonName() {
            return buttonName;
        }

        public String getIcon() {
            return icon;
        }

        public ActionType getActionType() {
            return actionType;
        }

        public String getActionValue() {
            return actionValue;
        }
    }
}