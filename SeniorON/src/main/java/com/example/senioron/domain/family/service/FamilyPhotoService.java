package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyPhotoCreateRequest;
import com.example.senioron.domain.family.dto.response.FamilyPhotoCreateResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FamilyPhotoService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    @Transactional
    public FamilyPhotoCreateResponse createPhoto(
            User principal,
            FamilyPhotoCreateRequest request
    ) {
        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Family family = user.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        String directory = "family-photos/" + family.getFamilyId();

        String imageKey = s3Service.upload(
                request.getImage(),
                directory
        );

        FamilyPhoto familyPhoto = FamilyPhoto.builder()
                .family(family)
                .user(user)
                .imageKey(imageKey)
                .build();

        FamilyPhoto savedPhoto = familyPhotoRepository.save(familyPhoto);

        return FamilyPhotoCreateResponse.builder()
                .familyPhotoId(savedPhoto.getFamilyPhotoId())
                .imageKey(savedPhoto.getImageKey())
                .uploaderName(user.getName())
                .createdAt(savedPhoto.getCreatedAt())
                .build();
    }
}
