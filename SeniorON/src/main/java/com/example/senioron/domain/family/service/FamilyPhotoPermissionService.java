package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
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

    private final FamilyPhotoSeniorRepository familyPhotoSeniorRepository;

    // 올린 사람 또는 공유 대상 시니어의 주 담당자만 삭제할 수 있다.
    public boolean canDelete(
            FamilyPhoto photo,
            User currentUser
    ) {
        return findDeletablePhotoIds(List.of(photo), currentUser)
                .contains(photo.getFamilyPhotoId());
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

        deletablePhotoIds.addAll(
                familyPhotoSeniorRepository
                        .findPhotoIdsSharedToSeniorsManagedBy(
                                otherUploaderPhotoIds,
                                currentUser,
                                ManagerType.PRIMARY
                        )
        );

        return deletablePhotoIds;
    }
}
