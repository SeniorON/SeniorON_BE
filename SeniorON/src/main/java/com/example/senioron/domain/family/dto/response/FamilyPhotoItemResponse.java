package com.example.senioron.domain.family.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FamilyPhotoItemResponse {
    private Long familyPhotoId;
    private String imageUrl;
    private String uploaderName;
    private String description;
    private LocalDateTime createdAt;
}
