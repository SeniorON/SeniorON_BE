package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;

import java.time.LocalDate;

public record SeniorCreateResponse(
        Long seniorId,
        String name,
        SeniorRelation relation,
        String customRelation,
        LocalDate birth,
        String phoneNumber,
        String address,
        String detailAddress
) {

    public static SeniorCreateResponse from(Senior senior) {
        return new SeniorCreateResponse(
                senior.getSeniorId(),
                senior.getName(),
                senior.getRelation(),
                senior.getCustomRelation(),
                senior.getBirth(),
                senior.getPhoneNumber(),
                senior.getAddress(),
                senior.getDetailAddress()
        );
    }
}