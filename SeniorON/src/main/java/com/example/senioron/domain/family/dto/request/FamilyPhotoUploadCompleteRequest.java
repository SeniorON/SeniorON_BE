package com.example.senioron.domain.family.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class FamilyPhotoUploadCompleteRequest {

    @NotNull(message = "시니어 ID는 필수입니다.")
    @Schema(description = "사진을 업로드하는 현재 가족의 시니어 ID", example = "1")
    private Long seniorId;

    @NotEmpty(message = "사진을 공유할 포토 그룹을 하나 이상 선택해주세요.")
    @Schema(description = "사진을 공유할 포토 그룹 ID 목록", example = "[1, 2]")
    private List<@NotNull(message = "포토 그룹 ID는 null일 수 없습니다.") Long> photoGroupIds;

    @NotBlank(message = "이미지 key는 필수입니다.")
    @Size(max = 512, message = "이미지 key가 너무 깁니다.")
    @Schema(description = "Presigned URL 발급 시 반환된 S3 객체 key",
            example = "family-photos/3/10/550e8400-e29b-41d4-a716-446655440000.jpg")
    private String imageKey;

    @Size(max = 30, message = "한마디는 30자 이내로 입력해주세요.")
    @Schema(description = "사진과 함께 남길 한마디", example = "오늘 공원에서 찍은 사진")
    private String description;

}