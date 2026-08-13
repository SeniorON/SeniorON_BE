package com.example.senioron.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "프로필 이미지 조회 응답")
public class ProfileImageResponse {

    @Schema(description = "사용자가 설정한 프로필 이미지 URL", nullable = true)
    private String profileImageUrl;

    @Schema(description = "기본 프로필 이미지 사용 여부", example = "true")
    private Boolean isDefaultProfileImage;
}
