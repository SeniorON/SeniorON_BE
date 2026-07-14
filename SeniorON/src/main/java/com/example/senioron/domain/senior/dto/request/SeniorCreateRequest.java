package com.example.senioron.domain.senior.dto.request;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

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

        String detailAddress
) {
}