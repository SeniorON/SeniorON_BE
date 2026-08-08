package com.example.senioron.domain.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "1:1 문의 등록 요청")
public class InquiryCreateRequest {

    @Schema(description = "문의 제목", example = "기기 연결이 계속 끊겨요")
    private String title;

    @Schema(description = "문의 내용", example = "인터넷 연결은 정상인데 계속 연결이 끊깁니다.")
    private String content;
}
