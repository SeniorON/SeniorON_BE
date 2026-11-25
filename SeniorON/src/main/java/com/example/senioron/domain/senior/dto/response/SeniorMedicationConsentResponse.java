package com.example.senioron.domain.senior.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonPropertyOrder({"connectionStatus", "medicationConsent"})
@Schema(description = "시니어 본인 계정의 복약 정보 동의 연결 및 동의 상태")
public class SeniorMedicationConsentResponse {

    @Schema(
            description = "시니어 프로필과 실제 PARENT 사용자 계정의 연결 상태",
            example = "CONNECTED",
            allowableValues = {"CONNECTED", "NOT_CONNECTED"}
    )
    private MedicationConsentConnectionStatus connectionStatus;

    @Schema(
            description = "연결된 시니어 본인 계정의 복약 정보 동의 여부. "
                    + "계정 미연결 또는 동의값 미설정 시 null",
            example = "true",
            nullable = true
    )
    private Boolean medicationConsent;
}
