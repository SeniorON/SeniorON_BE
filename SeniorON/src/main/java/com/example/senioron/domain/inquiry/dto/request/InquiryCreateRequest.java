
package com.example.senioron.domain.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "1:1 문의 등록 요청")
public class InquiryCreateRequest {

    @Schema(
            description = "문의 제목",
            example = "로그인이 안 돼요"
    )
    private String title;

    @Schema(
            description = "문의 내용",
            example = "로그인을 시도하면 오류가 발생합니다."
    )
    private String content;

    @Schema(
            description = "기기 모델 (선택)",
            example = "Galaxy A35",
            nullable = true
    )
    private String deviceModel;

    @Schema(
            description = "운영체제 버전 (선택)",
            example = "Android 14",
            nullable = true
    )
    private String osVersion;

    @Schema(
            description = "앱 버전 (선택)",
            example = "1.2.0",
            nullable = true
    )
    private String appVersion;
}
