package com.example.senioron.domain.event.dto.request;

import com.example.senioron.global.validation.CoordinatePairRequest;
import com.example.senioron.global.validation.ValidCoordinatePair;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@ValidCoordinatePair
public class InactivityRequest implements CoordinatePairRequest {
    @DecimalMin(value = "-90", inclusive = true)
    @DecimalMax(value = "90", inclusive = true)
    @Schema(example = "37.5665")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", inclusive = true)
    @DecimalMax(value = "180", inclusive = true)
    @Schema(example = "126.9780")
    private BigDecimal longitude;

    @NotNull @Min(0) @Max(100)
    private Integer deviceBattery;

    @NotNull @PastOrPresent
    private LocalDateTime lastSeenAt;

    @Size(min = 1, max = 128)
    @Pattern(regexp = "(?s).*\\S.*", message = "clientEventId는 공백만으로 구성할 수 없습니다.")
    @Schema(description = "앱이 생성한 이벤트별 고유 식별자. 재전송 시 같은 값을 사용하면 최초 무활동 응답을 반환합니다.")
    private String clientEventId;

    @Override
    public BigDecimal latitude() {
        return latitude;
    }

    @Override
    public BigDecimal longitude() {
        return longitude;
    }
}
