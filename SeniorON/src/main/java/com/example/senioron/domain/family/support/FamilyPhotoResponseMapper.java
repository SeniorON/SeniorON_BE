package com.example.senioron.domain.family.support;

import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.storage.S3Service;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FamilyPhotoResponseMapper {

    private final S3Service s3Service;

    public FamilyPhotoItemResponse toItemResponse(
            FamilyPhoto photo,
            User currentUser,
            LocalDateTime newPhotoCutoff,
            boolean viewedByCurrentParent,
            boolean canDelete
    ) {
        boolean newPhoto =
                currentUser.getRole() == Role.PARENT
                        && photo.getUser().getRole() == Role.CHILD
                        && !viewedByCurrentParent
                        && !photo.getCreatedAt()
                        .isBefore(newPhotoCutoff);

        String imageUrl = s3Service.getFileUrl(photo.getImageKey());
        String thumbnailKey = photo.getThumbnailKey();

        String thumbnailUrl = thumbnailKey == null || thumbnailKey.isBlank()
                ? imageUrl
                : s3Service.getFileUrl(thumbnailKey);

        return FamilyPhotoItemResponse.builder()
                .familyPhotoId(photo.getFamilyPhotoId())
                .imageUrl(imageUrl)
                .thumbnailUrl(thumbnailUrl)
                .uploaderUserId(
                        photo.getUser().getUsersId()
                )
                .uploaderName(photo.getUser().getName())
                .description(photo.getDescription())
                .canDelete(canDelete)
                .createdAt(photo.getCreatedAt())
                .newPhoto(newPhoto)
                .build();
    }
}
