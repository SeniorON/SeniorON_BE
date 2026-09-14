package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FamilyPhotoPermissionService {

    private final PhotoGroupFamilyRepository photoGroupFamilyRepository;

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
                        && photoGroupFamilyRepository.existsByFamilyAndPhotoGroup(
                        photo.getUser().getFamily(),
                        photo.getPhotoGroup()
                );

        boolean isPrimaryManager =
                currentUser.getManagerType() == ManagerType.PRIMARY;

        return isUploader
                || (!uploaderStillInFamily && isPrimaryManager);
    }
}
