package com.example.senioron.domain.senior.dto.request;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.global.validation.CoordinatePairRequest;
import com.example.senioron.global.validation.ValidCoordinatePair;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

@ValidCoordinatePair
public record SeniorCreateRequest(

        @NotBlank(message = "이름을 입력해 주세요.")
        String name,

        @NotNull(message = "관계를 선택해 주세요.")
        SeniorRelation relation,

        String customRelation,

        @NotNull(message = "생년월일을 입력해 주세요.")
        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        LocalDate birth,

        @NotBlank(message = "전화번호를 입력해 주세요.")
        @Pattern(
                regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
                message = "올바른 전화번호 형식이 아닙니다."
        )
        String phoneNumber,

        String address,

        String detailAddress,

        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        Double longitude
) implements CoordinatePairRequest {
}
