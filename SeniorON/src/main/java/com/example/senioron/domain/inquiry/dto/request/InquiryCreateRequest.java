
package com.example.senioron.domain.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
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

    @Size(max = 255, message = "기기 모델명은 255자 이하여야 합니다.")
    @Schema(description = "기기 모델명 (선택)", example = "Galaxy S24")
    private String deviceModel;

    @Size(max = 255, message = "OS 버전은 255자 이하여야 합니다.")
    @Schema(description = "OS 버전 (선택)", example = "Android 14")
    private String osVersion;

    @Size(max = 255, message = "앱 버전은 255자 이하여야 합니다.")
    @Schema(description = "앱 버전 (선택)", example = "1.2.0")
    private String appVersion;
}
