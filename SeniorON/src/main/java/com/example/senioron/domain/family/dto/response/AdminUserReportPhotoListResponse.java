package com.example.senioron.domain.family.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "관리자 게시자 업로드 사진 목록 조회 응답")
public record AdminUserReportPhotoListResponse(

        @Schema(description = "게시자 사용자 ID", example = "20")
        Long userId,

        @Schema(description = "게시자 이름 (\"OOO님의 업로드 사진\"에 표시)", example = "김지은")
        String userName,

        @Schema(description = "업로드 사진 (최신순)")
        List<Photo> content,

        @Schema(description = "페이지 번호 (0부터 시작)", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "18")
        int size,

        @Schema(description = "전체 업로드 사진 수 (총 장 수, 사진 뷰어의 전체 장 수)", example = "100")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "6")
        int totalPages
) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "업로드 사진")
    public record Photo(

            @Schema(description = "사진 ID", example = "40")
            Long familyPhotoId,

            @Schema(description = "썸네일 URL. 썸네일이 없으면 원본 URL")
            String thumbnailUrl,

            @Schema(description = "원본 URL (사진 뷰어에 사용)")
            String imageUrl,

            @Schema(description = "업로드일시")
            LocalDateTime uploadedAt
    ) {
    }
}
