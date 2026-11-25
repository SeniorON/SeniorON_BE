package com.example.senioron.domain.event.dto.request;

import com.example.senioron.domain.event.entity.OutingDetectionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutingReturnRequestTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void optionalMetadataIsDeserialized() throws Exception {
        var request = mapper.readValue("""
                {"phase":"RETURN","clientEventId":"event-123","detectedAt":"2026-10-19T05:00:00Z"}
                """, OutingReturnRequest.class);
        assertThat(request.getClientEventId()).isEqualTo("event-123");
        assertThat(request.getDetectedAt().toInstant()).isEqualTo(java.time.Instant.parse("2026-10-19T05:00:00Z"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", " ", "\n\t"})
    void blankClientEventIdIsRejected(String id) {
        var request = OutingReturnRequest.builder().phase(com.example.senioron.domain.event.entity.OutingPhase.OUTING)
                .latitude(java.math.BigDecimal.ONE).longitude(java.math.BigDecimal.ONE).deviceBattery(80)
                .clientEventId(id).build();
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).anyMatch(v -> v.getPropertyPath().toString().equals("clientEventId"));
        }
    }

    @Test
    void absentClientEventIdIsValidAndOversizedIdIsRejected() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var request = OutingReturnRequest.builder().phase(com.example.senioron.domain.event.entity.OutingPhase.OUTING)
                    .latitude(java.math.BigDecimal.ONE).longitude(java.math.BigDecimal.ONE).deviceBattery(80).build();
            assertThat(factory.getValidator().validate(request)).isEmpty();
            var oversized = OutingReturnRequest.builder().clientEventId("x".repeat(129)).build();
            assertThat(factory.getValidator().validate(oversized)).anyMatch(v -> v.getPropertyPath().toString().equals("clientEventId"));
        }
    }

    @Test
    void omittedDetectionTypeDefaultsToTransition() throws Exception {
        assertThat(mapper.readValue("{\"phase\":\"OUTING\"}", OutingReturnRequest.class)
                .getDetectionType()).isEqualTo(OutingDetectionType.TRANSITION);
    }

    @Test
    void nullDetectionTypeDefaultsToTransition() throws Exception {
        assertThat(mapper.readValue("{\"phase\":\"RETURN\",\"detectionType\":null}", OutingReturnRequest.class)
                .getDetectionType()).isEqualTo(OutingDetectionType.TRANSITION);
    }

    @ParameterizedTest
    @EnumSource(OutingDetectionType.class)
    void explicitDetectionTypeIsPreserved(OutingDetectionType type) throws Exception {
        String json = "{\"phase\":\"OUTING\",\"detectionType\":\"" + type.name() + "\"}";
        assertThat(mapper.readValue(json, OutingReturnRequest.class).getDetectionType()).isEqualTo(type);
    }

    @Test
    void unknownDetectionTypeIsRejected() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"phase\":\"OUTING\",\"detectionType\":\"UNKNOWN\"}", OutingReturnRequest.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void builderWithoutDetectionTypeDefaultsToTransition() {
        assertThat(OutingReturnRequest.builder().build().getDetectionType())
                .isEqualTo(OutingDetectionType.TRANSITION);
    }
}
