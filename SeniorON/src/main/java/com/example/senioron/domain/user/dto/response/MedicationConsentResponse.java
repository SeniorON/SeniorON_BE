package com.example.senioron.domain.user.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonPropertyOrder({"medicationConsent"})
public class MedicationConsentResponse {

    private boolean medicationConsent;
}
