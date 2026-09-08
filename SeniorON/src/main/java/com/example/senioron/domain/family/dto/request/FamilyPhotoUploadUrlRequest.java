package com.example.senioron.domain.family.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FamilyPhotoUploadUrlRequest {

    @NotBlank(message = "이미지 형식은 필수입니다.")
    @Schema(description = "업로드할 이미지의 Content-Type", example = "image/jpeg",
            allowableValues = {
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            }
    )
    private String contentType;

    @NotNull(message = "이미지 크기는 필수입니다.")
    @Positive(message = "이미지 크기는 0보다 커야 합니다.")
    @Max(value = 10 * 1024 * 1024, message = "사진 크기는 10MB 이하여야 합니다.")
    @Schema(description = "업로드할 이미지의 바이트 크기", example = "5242880", maximum = "10485760")
    private Long fileSize;
}