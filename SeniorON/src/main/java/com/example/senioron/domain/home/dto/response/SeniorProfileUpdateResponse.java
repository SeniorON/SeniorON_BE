package com.example.senioron.domain.home.dto.response;

import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;

import java.time.LocalDate;

public record SeniorProfileUpdateResponse(
        Long seniorId,
        String name,
        SeniorRelation relation,
        String customRelation,
        LocalDate birth,
        String phoneNumber,
        String address,
        String detailAddress,
        Double latitude,
        Double longitude
) {

    public static SeniorProfileUpdateResponse from(
            Senior senior,
            SeniorRelation relation,
            String customRelation
    ) {
        return new SeniorProfileUpdateResponse(
                senior.getSeniorId(),
                senior.getName(),
                relation,
                customRelation,
                senior.getBirth(),
                senior.getPhoneNumber(),
                senior.getAddress(),
                senior.getDetailAddress(),
                senior.getLatitude(),
                senior.getLongitude()
        );
    }
}
