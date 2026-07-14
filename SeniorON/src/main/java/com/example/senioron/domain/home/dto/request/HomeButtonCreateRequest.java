package com.example.senioron.domain.home.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HomeButtonCreateRequest {

    @NotNull
    private Long optionId;
}