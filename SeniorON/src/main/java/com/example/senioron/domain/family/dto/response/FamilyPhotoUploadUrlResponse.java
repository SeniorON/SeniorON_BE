package com.example.senioron.domain.family.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FamilyPhotoUploadUrlResponse {

    @Schema(
            description = "S3에 저장될 이미지 객체 key",
            example = "family-photos/3/10/550e8400-e29b-41d4-a716-446655440000.jpg"
    )
    private String imageKey;

    @Schema(
            description = "이미지 업로드용 Presigned PUT URL"
    )
    private String uploadUrl;

    @Schema(
            description = "Presigned URL 유효시간(초)",
            example = "300"
    )
    private long expiresInSeconds;
}