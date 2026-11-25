package com.example.senioron.domain.event.dto.request;

import com.example.senioron.domain.event.entity.OutingPhase;
import com.example.senioron.domain.event.entity.OutingDetectionType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;


import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
public class OutingReturnRequest {

    @NotNull
    private OutingPhase phase;

    @Schema(description = "외출·귀가 판단 유형. 생략 또는 null이면 TRANSITION", defaultValue = "TRANSITION")
    private OutingDetectionType detectionType;

    @Size(min = 1, max = 128)
    @Pattern(regexp = "(?s).*\\S.*", message = "clientEventId는 공백만으로 구성할 수 없습니다.")
    @Schema(description = "앱이 생성한 이벤트 식별자. 사용자별 중복 방지 키이며 재전송 시 같은 값 사용. 같은 키는 최초 저장 결과 반환")
    private String clientEventId;

    @Schema(description = "오프셋을 포함한 감지 시각. RECONNECTION이면 이동 시각이 아닌 상태 확인 시각", example = "2026-10-19T14:00:00+09:00")
    private OffsetDateTime detectedAt;

    @NotNull
    @DecimalMin(value = "-90", inclusive = true)
    @DecimalMax(value = "90", inclusive = true)
    @Schema(example = "37.5665")
    private BigDecimal latitude;

    @NotNull
    @DecimalMin(value = "-180", inclusive = true)
    @DecimalMax(value = "180", inclusive = true)
    @Schema(example = "126.9780")
    private BigDecimal longitude;

    @NotNull @Min(0) @Max(100)
    private Integer deviceBattery;

    @Builder
    @JsonCreator
    public OutingReturnRequest(
            @JsonProperty("phase") OutingPhase phase,
            @JsonProperty("latitude") BigDecimal latitude,
            @JsonProperty("longitude") BigDecimal longitude,
            @JsonProperty("deviceBattery") Integer deviceBattery,
            @JsonProperty("detectionType") OutingDetectionType detectionType,
            @JsonProperty("clientEventId") String clientEventId,
            @JsonProperty("detectedAt") OffsetDateTime detectedAt
    ) {
        this.phase = phase;
        this.detectionType = detectionType == null ? OutingDetectionType.TRANSITION : detectionType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.deviceBattery = deviceBattery;
        this.clientEventId = clientEventId;
        this.detectedAt = detectedAt;
    }

    public OutingReturnRequest(OutingPhase phase, BigDecimal latitude, BigDecimal longitude,
                               Integer deviceBattery, OutingDetectionType detectionType) {
        this(phase, latitude, longitude, deviceBattery, detectionType, null, null);
    }

    public OutingReturnRequest(OutingPhase phase, BigDecimal latitude, BigDecimal longitude,
                               Integer deviceBattery) {
        this(phase, latitude, longitude, deviceBattery, OutingDetectionType.TRANSITION);
    }
}
