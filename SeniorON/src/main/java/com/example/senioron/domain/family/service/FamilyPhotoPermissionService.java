package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FamilyPhotoPermissionService {

    private final FamilyPhotoGroupRepository familyPhotoGroupRepository;

    public boolean canDelete(
            FamilyPhoto photo,
            User currentUser
    ) {
        boolean isUploader = Objects.equals(
                photo.getUser().getUsersId(),
                currentUser.getUsersId()
        );

        if (isUploader) {
            return true;
        }

        boolean uploaderStillHasAccess =
                familyPhotoGroupRepository
                        .existsAccessibleByFamilyPhotoAndUser(
                                photo,
                                photo.getUser()
                        );

        if (uploaderStillHasAccess) {
            return false;
        }

        return familyPhotoGroupRepository
                .existsAccessibleByFamilyPhotoAndUserAndManagerType(
                        photo,
                        currentUser,
                        ManagerType.PRIMARY
                );
    }

    public Set<Long> findDeletablePhotoIds(
            List<FamilyPhoto> photos,
            User currentUser
    ) {
        Set<Long> deletablePhotoIds = new HashSet<>();
        List<Long> otherUploaderPhotoIds = new ArrayList<>();

        for (FamilyPhoto photo : photos) {
            boolean isUploader = Objects.equals(
                    photo.getUser().getUsersId(),
                    currentUser.getUsersId()
            );

            if (isUploader) {
                deletablePhotoIds.add(photo.getFamilyPhotoId());
            } else {
                otherUploaderPhotoIds.add(photo.getFamilyPhotoId());
            }
        }

        if (otherUploaderPhotoIds.isEmpty()) {
            return deletablePhotoIds;
        }

        Set<Long> uploaderAccessiblePhotoIds =
                familyPhotoGroupRepository
                        .findPhotoIdsAccessibleByUploader(
                                otherUploaderPhotoIds
                        );

        List<Long> uploaderLostAccessPhotoIds =
                otherUploaderPhotoIds.stream()
                        .filter(photoId ->
                                !uploaderAccessiblePhotoIds.contains(photoId)
                        )
                        .toList();

        if (uploaderLostAccessPhotoIds.isEmpty()) {
            return deletablePhotoIds;
        }

        deletablePhotoIds.addAll(
                familyPhotoGroupRepository
                        .findPhotoIdsAccessibleByUserAndManagerType(
                                uploaderLostAccessPhotoIds,
                                currentUser,
                                ManagerType.PRIMARY
                        )
        );

        return deletablePhotoIds;
    }
}
