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
        String detailAddress
) {

    public static SeniorProfileUpdateResponse from(
            Senior senior
    ) {
        return new SeniorProfileUpdateResponse(
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