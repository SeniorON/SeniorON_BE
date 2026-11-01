package com.example.senioron.domain.family.dto.request;

import com.example.senioron.domain.family.entity.PhotoReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FamilyPhotoReportRequest {

    @NotNull(message = "신고 사유는 필수입니다.")
    @Schema(description = "신고 사유", example = "ADVERTISEMENT")
    private PhotoReportReason reason;

    @Size(max = 200, message = "자세한 내용은 200자 이하로 입력해 주세요.")
    @Schema(description = "자세한 신고 내용 (선택)", example = "광고 사진이 반복해서 올라와요.", nullable = true)
    private String detail;

    @NotNull(message = "사진 숨김 여부는 필수입니다.")
    @Schema(description = "이 사진 다시 보지 않기 여부", example = "true")
    private Boolean hide;
}