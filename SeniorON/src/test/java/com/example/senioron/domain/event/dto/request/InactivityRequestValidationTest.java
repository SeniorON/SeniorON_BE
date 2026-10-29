package com.example.senioron.domain.event.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class InactivityRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void locationIsOptional() {
        InactivityRequest request = new InactivityRequest();
        ReflectionTestUtils.setField(request, "deviceBattery", 80);
        ReflectionTestUtils.setField(request, "lastSeenAt", LocalDateTime.now().minusMinutes(10));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void latitudeWithoutLongitudeIsRejected() {
        InactivityRequest request = validRequest();
        ReflectionTestUtils.setField(request, "latitude", new BigDecimal("37.5665"));

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().equals("위도와 경도는 함께 입력해야 합니다."));
    }

    @Test
    void longitudeWithoutLatitudeIsRejected() {
        InactivityRequest request = validRequest();
        ReflectionTestUtils.setField(request, "longitude", new BigDecimal("126.9780"));

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().equals("위도와 경도는 함께 입력해야 합니다."));
    }

    private InactivityRequest validRequest() {
        InactivityRequest request = new InactivityRequest();
        ReflectionTestUtils.setField(request, "deviceBattery", 80);
        ReflectionTestUtils.setField(request, "lastSeenAt", LocalDateTime.now().minusMinutes(10));
        return request;
    }
}
