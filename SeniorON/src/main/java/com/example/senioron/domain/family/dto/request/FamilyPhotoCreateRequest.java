package com.example.senioron.domain.family.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class FamilyPhotoCreateRequest {

    @NotNull(message = "사진은 필수입니다.")
    @Schema(description = "등록할 가족 사진", type = "string", format = "binary")
    private MultipartFile image;

}
