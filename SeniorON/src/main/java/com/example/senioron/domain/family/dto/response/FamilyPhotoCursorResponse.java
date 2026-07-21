package com.example.senioron.domain.family.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FamilyPhotoCursorResponse {

    private LocalDateTime createdAt;
    private Long familyPhotoId;
}
