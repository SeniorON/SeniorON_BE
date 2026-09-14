package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FamilyPhotoPersistenceService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final UserRepository userRepository;
    private final PhotoGroupRepository photoGroupRepository;
    private final PhotoGroupFamilyRepository photoGroupFamilyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FamilyPhoto create(
            Long userId,
            String imageKey,
            String idempotencyKey,
            String description
    ) {
        User user = userRepository.findByIdWithFamily(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        Family family = user.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        PhotoGroup photoGroup = getOrCreateDefaultPhotoGroup(family);

        FamilyPhoto familyPhoto = FamilyPhoto.builder()
                .photoGroup(photoGroup)
                .user(user)
                .imageKey(imageKey)
                .idempotencyKey(idempotencyKey)
                .description(description)
                .build();

        return familyPhotoRepository.saveAndFlush(familyPhoto);
    }

    private PhotoGroup getOrCreateDefaultPhotoGroup(Family family) {
        return photoGroupFamilyRepository
                .findFirstByFamilyOrderByIdAsc(family)
                .map(PhotoGroupFamily::getPhotoGroup)
                .orElseGet(() -> {
                    PhotoGroup photoGroup = photoGroupRepository.save(
                            PhotoGroup.builder()
                                    .name("Family " + family.getFamilyId())
                                    .build()
                    );

                    photoGroupFamilyRepository.save(
                            PhotoGroupFamily.builder()
                                    .family(family)
                                    .photoGroup(photoGroup)
                                    .build()
                    );

                    return photoGroup;
                });
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
