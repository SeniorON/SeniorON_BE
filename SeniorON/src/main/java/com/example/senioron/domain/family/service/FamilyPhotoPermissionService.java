package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class FamilyPhotoPermissionService {

    public boolean canDelete(
            FamilyPhoto photo,
            User currentUser
    ) {
        boolean isUploader = Objects.equals(
                photo.getUser().getUsersId(),
                currentUser.getUsersId()
        );

        boolean uploaderStillInFamily =
                photo.getUser().getFamily() != null
                        && Objects.equals(
                        photo.getUser().getFamily().getFamilyId(),
                        photo.getFamily().getFamilyId()
                );

        boolean isPrimaryManager =
                currentUser.getManagerType() == ManagerType.PRIMARY;

        return isUploader
                || (!uploaderStillInFamily && isPrimaryManager);
    }
}