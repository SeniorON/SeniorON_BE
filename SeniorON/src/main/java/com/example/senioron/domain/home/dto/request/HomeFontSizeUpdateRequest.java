package com.example.senioron.domain.home.dto.request;

import com.example.senioron.domain.home.entity.FontSize;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class HomeFontSizeUpdateRequest {

    @NotNull
    @JsonProperty("font_size")
    private FontSize fontSize;
}