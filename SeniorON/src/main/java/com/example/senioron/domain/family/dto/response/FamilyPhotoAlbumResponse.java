package com.example.senioron.domain.family.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FamilyPhotoAlbumResponse {

    private Long uploaderUserId;

    private String uploaderName;

    private String latestPhotoUrl;

    private long photoCount;

    private boolean hasNewPhotos;
}
