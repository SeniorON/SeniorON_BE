package com.example.senioron.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.home.dto.request.SeniorProfileUpdateRequest;
import com.example.senioron.domain.senior.dto.request.SeniorCreateRequest;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ValidCoordinatePairTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void seniorCreateRequestAllowsBothCoordinatesMissing() {
        SeniorCreateRequest request = createSeniorCreateRequest(null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void seniorCreateRequestAllowsBothCoordinatesPresent() {
        SeniorCreateRequest request = createSeniorCreateRequest(37.5665, 126.9780);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void seniorCreateRequestRejectsLatitudeOnly() {
        SeniorCreateRequest request = createSeniorCreateRequest(37.5665, null);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().equals("위도와 경도는 함께 입력해야 합니다."));
    }

    @Test
    void seniorCreateRequestRejectsLongitudeOnly() {
        SeniorCreateRequest request = createSeniorCreateRequest(null, 126.9780);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().equals("위도와 경도는 함께 입력해야 합니다."));
    }

    @Test
    void seniorProfileUpdateRequestRejectsPartialCoordinates() {
        SeniorProfileUpdateRequest request = createSeniorProfileUpdateRequest(37.5665, null);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().equals("위도와 경도는 함께 입력해야 합니다."));
    }

    @Test
    void seniorProfileUpdateRequestAllowsBothCoordinatesPresent() {
        SeniorProfileUpdateRequest request = createSeniorProfileUpdateRequest(37.5665, 126.9780);

        assertThat(validator.validate(request)).isEmpty();
    }

    private SeniorCreateRequest createSeniorCreateRequest(Double latitude, Double longitude) {
        return new SeniorCreateRequest(
                "김영희",
                SeniorRelation.MOTHER,
                null,
                LocalDate.of(1950, 1, 1),
                "010-1234-5678",
                "서울시",
                "101호",
                latitude,
                longitude
        );
    }

    private SeniorProfileUpdateRequest createSeniorProfileUpdateRequest(Double latitude, Double longitude) {
        return new SeniorProfileUpdateRequest(
                "김영희",
                SeniorRelation.MOTHER,
                null,
                LocalDate.of(1950, 1, 1),
                "010-1234-5678",
                "서울시",
                "101호",
                latitude,
                longitude
        );
    }
}
