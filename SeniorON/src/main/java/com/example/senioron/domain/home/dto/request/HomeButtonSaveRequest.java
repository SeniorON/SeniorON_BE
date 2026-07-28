package com.example.senioron.domain.home.dto.request;

import com.example.senioron.domain.home.entity.MusicApp;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class HomeButtonSaveRequest {

    private MusicApp musicApp;

    @Valid
    @NotEmpty(message = "버튼 목록은 비어 있을 수 없습니다.")
    private List<ButtonRequest> buttons;

    @Getter
    @NoArgsConstructor
    public static class ButtonRequest {

        @JsonProperty("option_id")
        @NotNull(message = "버튼 옵션 ID는 필수입니다.")
        private Long optionId;

        @JsonProperty("button_order")
        @NotNull(message = "버튼 순서는 필수입니다.")
        private Integer buttonOrder;

        @JsonProperty("button_name")
        private String buttonName;
    }
}