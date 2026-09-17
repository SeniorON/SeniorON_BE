package com.example.senioron.domain.family.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PhotoGroupConnectRequest {

    @NotNull
    private Long seniorId;

    @NotBlank
    private String seniorCode;
}