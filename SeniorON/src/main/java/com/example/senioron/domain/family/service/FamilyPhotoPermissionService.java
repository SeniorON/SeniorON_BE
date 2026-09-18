package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

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
}
