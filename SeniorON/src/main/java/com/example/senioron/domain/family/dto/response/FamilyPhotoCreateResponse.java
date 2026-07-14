package com.example.senioron.domain.family.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FamilyPhotoCreateResponse {

    private Long familyPhotoId;

    private String imageKey;

    private String uploaderName;

    private LocalDateTime createdAt;
}
