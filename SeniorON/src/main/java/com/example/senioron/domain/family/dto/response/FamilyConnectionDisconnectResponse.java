package com.example.senioron.domain.family.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "가족 연결 해제 결과")
public record FamilyConnectionDisconnectResponse(
        @Schema(
                description = "연결 해제한 가족 ID",
                example = "1"
        )
        Long familyId,

        @Schema(
                description = "연결 해제 후 Family 객체까지 삭제되었는지 여부",
                example = "true"
        )
        boolean familyDeleted
) {
}
