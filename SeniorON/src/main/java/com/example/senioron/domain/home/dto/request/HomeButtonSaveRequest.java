package com.example.senioron.domain.home.dto.request;

import com.example.senioron.domain.home.entity.MusicApp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.example.senioron.domain.home.entity.ActionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HomeButtonSaveRequest {

    private MusicApp musicApp;

    @NotNull
    @Valid
    private List<ButtonRequest> buttons;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ButtonRequest {

        private Integer buttonOrder;

        private String buttonName;

        private ActionType actionType;

        private String actionValue;

        private String packageName;
    }
}