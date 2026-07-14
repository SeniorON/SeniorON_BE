package com.example.senioron.domain.home.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class HomeButtonUpdateRequest {

    @NotNull(message = "버튼 목록은 필수입니다.")
    @Valid
    private List<ButtonRequest> buttons;

    public List<ButtonRequest> getButtons() {
        return buttons;
    }

    public static class ButtonRequest {

        @JsonProperty("button_id")
        @NotNull(message = "버튼 ID는 필수입니다.")
        private Long buttonId;

        @JsonProperty("button_order")
        @NotNull(message = "버튼 순서는 필수입니다.")
        private Integer buttonOrder;

        @JsonProperty("button_name")
        private String buttonName;

        private String icon;

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
    }
}