package com.example.senioron.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MedicationConsentUpdateRequest {

    @NotNull(message = "복약 정보 동의 여부를 입력해 주세요.")
    private Boolean medicationConsent;
}
