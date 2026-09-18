package com.example.senioron.domain.family.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class FamilyPhotoCreateRequest {

    @NotNull(message = "시니어 ID는 필수입니다.")
    @Schema(description = "사진을 업로드하는 현재 가족의 시니어 ID", example = "1")
    private Long seniorId;

    @NotEmpty(message = "사진을 공유할 포토 그룹을 하나 이상 선택해주세요.")
    @Schema(description = "사진을 공유할 포토 그룹 ID 목록", example = "[1, 2]")
    private List<@NotNull(message = "포토 그룹 ID는 null일 수 없습니다.") Long> photoGroupIds;

    @NotNull(message = "사진은 필수입니다.")
    @Schema(description = "등록할 가족 사진", type = "string", format = "binary")
    private MultipartFile image;

    @Size(max = 30, message = "한마디는 30자 이내로 입력해주세요.")
    @Schema(description = "사진과 함께 남길 한마디 (선택)")
    private String description;

}
