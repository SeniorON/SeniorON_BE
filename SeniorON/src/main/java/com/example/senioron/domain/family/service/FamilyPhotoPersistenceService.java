package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
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

@Service
@RequiredArgsConstructor
public class FamilyPhotoPersistenceService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FamilyPhoto create(
            Long userId,
            String imageKey,
            String idempotencyKey,
            String description
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        Family family = user.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        FamilyPhoto familyPhoto = FamilyPhoto.builder()
                .family(family)
                .user(user)
                .imageKey(imageKey)
                .idempotencyKey(idempotencyKey)
                .description(description)
                .build();

        return familyPhotoRepository.saveAndFlush(familyPhoto);
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
