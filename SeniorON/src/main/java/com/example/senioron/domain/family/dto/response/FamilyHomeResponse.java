package com.example.senioron.domain.family.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FamilyHomeResponse {

    // 함께하는 가족
    private List<FamilyMemberResponse> members;

    // 최근 사진을 올린 서로 다른 구성원 최대 3명의 프로필 이미지
    private List<String> recentUploaderProfileImageUrls;

    // 최근 가족 사진 최대 4장
    private List<FamilyPhotoItemResponse> recentPhotos;
}
