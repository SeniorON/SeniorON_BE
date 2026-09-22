package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Optional;
import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyPhotoPersistenceService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final UserRepository userRepository;
    private final FamilyPhotoGroupRepository familyPhotoGroupRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FamilyPhoto create(
            Long userId,
            String imageKey,
            String idempotencyKey,
            String description,
            List<PhotoGroup> photoGroups
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        PhotoGroup representativePhotoGroup =
                photoGroups.stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN
                                )
                        );

        FamilyPhoto familyPhoto = FamilyPhoto.builder()
                .photoGroup(representativePhotoGroup)
                .user(user)
                .imageKey(imageKey)
                .idempotencyKey(idempotencyKey)
                .description(description)
                .build();

        FamilyPhoto savedPhoto =
                familyPhotoRepository.saveAndFlush(familyPhoto);

        List<FamilyPhotoGroup> mappings =
                photoGroups.stream()
                        .map(photoGroup ->
                                FamilyPhotoGroup.builder()
                                        .familyPhoto(savedPhoto)
                                        .photoGroup(photoGroup)
                                        .build()
                        )
                        .toList();

        familyPhotoGroupRepository.saveAllAndFlush(mappings);

        return savedPhoto;
    }

    @Transactional(readOnly = true)
    public Optional<FamilyPhoto> findExisting(
            Long userId,
            String idempotencyKey
    ) {
        return familyPhotoRepository
                .findByUserUsersIdAndIdempotencyKey(
                        userId,
                        idempotencyKey
                );
    }
}
