package com.example.senioron.domain.home.dto.request;

import com.example.senioron.domain.home.entity.MusicApp;
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
    @NotEmpty
    private List<ButtonRequest> buttons;

    @Getter
    @NoArgsConstructor
    public static class ButtonRequest {

        @NotNull
        private Long optionId;

        @NotNull
        private Integer buttonOrder;
    }
}