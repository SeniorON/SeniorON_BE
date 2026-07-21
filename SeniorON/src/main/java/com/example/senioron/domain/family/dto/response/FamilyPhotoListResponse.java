package com.example.senioron.domain.family.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class FamilyPhotoListResponse {
    private List<FamilyPhotoItemResponse> photos;
    private FamilyPhotoCursorResponse nextCursor;
    private boolean hasNext;
}
