package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.UserSenior;

import java.time.LocalDate;

public record SeniorCreateResponse(
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

    public static SeniorCreateResponse from(Senior senior, UserSenior userSenior) {
        return new SeniorCreateResponse(
                senior.getSeniorId(),
                senior.getName(),
                userSenior.getRelation(),
                userSenior.getCustomRelation(),
                senior.getBirth(),
                senior.getPhoneNumber(),
                senior.getAddress(),
                senior.getDetailAddress(),
                senior.getLatitude(),
                senior.getLongitude()
        );
    }
}
