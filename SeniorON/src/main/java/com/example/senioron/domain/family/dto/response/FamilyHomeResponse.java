package com.example.senioron.domain.family.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FamilyHomeResponse {

    // 함께하는 가족
    private List<FamilyMemberResponse> members;

    // 최근 가족 사진 최대 4장
    private List<FamilyPhotoItemResponse> recentPhotos;
}
