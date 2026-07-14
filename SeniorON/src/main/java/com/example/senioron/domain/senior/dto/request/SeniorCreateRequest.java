package com.example.senioron.domain.senior.dto.request;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SeniorCreateRequest(

        @NotBlank(message = "이름을 입력해 주세요.")
        String name,

        @NotNull(message = "관계를 선택해 주세요.")
        SeniorRelation relation,

        String customRelation,

        @NotNull(message = "생년월일을 입력해 주세요.")
        LocalDate birth,

        @NotBlank(message = "전화번호를 입력해 주세요.")
        String phoneNumber,

        String address,

        String detailAddress
) {
}