package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.Senior;
import java.time.LocalDate;

public record SeniorFamilyResponse(
        Long seniorId,
        String name,
        LocalDate birth,
        String phoneNumber,
        String address,
        String detailAddress,
        Double latitude,
        Double longitude,
        boolean parentLinked
) {

    public static SeniorFamilyResponse from(Senior senior) {
        return new SeniorFamilyResponse(
                senior.getSeniorId(),
                senior.getName(),
                senior.getBirth(),
                senior.getPhoneNumber(),
                senior.getAddress(),
                senior.getDetailAddress(),
                senior.getLatitude(),
                senior.getLongitude(),
                senior.getParentUser() != null
        );
    }
}
