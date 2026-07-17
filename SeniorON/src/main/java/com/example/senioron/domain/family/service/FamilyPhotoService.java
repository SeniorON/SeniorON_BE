package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyPhotoCreateRequest;
import com.example.senioron.domain.family.dto.response.FamilyPhotoCreateResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoListResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

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

        try {
            FamilyPhoto familyPhoto = FamilyPhoto.builder()
                    .family(family)
                    .user(user)
                    .imageKey(imageKey)
                    .description(request.getDescription())
                    .build();

            FamilyPhoto savedPhoto = familyPhotoRepository.saveAndFlush(familyPhoto);

            return FamilyPhotoCreateResponse.builder()
                    .familyPhotoId(savedPhoto.getFamilyPhotoId())
                    .imageKey(savedPhoto.getImageKey())
                    .uploaderName(user.getName())
                    .description(savedPhoto.getDescription())
                    .createdAt(savedPhoto.getCreatedAt())
                    .build();
        } catch(RuntimeException e) {
            s3Service.delete(imageKey);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public FamilyPhotoListResponse getPhotos(
            User principal,
            Long cursor,
            int size
    ) {
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("사진 조회 개수는 1~50이어야 합니다.");
        }

        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Family family = user.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        Pageable pageable = PageRequest.of(0, size + 1);

        List<FamilyPhoto> fetchedPhotos;

        if (cursor == null) {
            fetchedPhotos = familyPhotoRepository.findByFamilyOrderByFamilyPhotoIdDesc(
                    family,
                    pageable
            );
        } else {
            fetchedPhotos = familyPhotoRepository.findByFamilyAndFamilyPhotoIdLessThanOrderByFamilyPhotoIdDesc(
                    family,
                    cursor,
                    pageable
            );
        }

        boolean hasNext = fetchedPhotos.size() > size;

        List<FamilyPhoto> pagePhotos = hasNext
                ? fetchedPhotos.subList(0, size)
                : fetchedPhotos;

        Long nextCursor = hasNext
                ? pagePhotos.get(pagePhotos.size() - 1).getFamilyPhotoId()
                : null;

        List<FamilyPhotoItemResponse> photoResponses = pagePhotos.stream()
                .map(photo -> FamilyPhotoItemResponse.builder()
                        .familyPhotoId(photo.getFamilyPhotoId())
                        .imageUrl(s3Service.getFileUrl(photo.getImageKey()))
                        .uploaderName(photo.getUser().getName())
                        .description(photo.getDescription())
                        .createdAt(photo.getCreatedAt())
                        .build())
                .toList();

        return FamilyPhotoListResponse.builder()
                .photos(photoResponses)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    @Transactional
    public void deletePhoto(User principal, Long familyPhotoId) {
        User currentUser = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Family family = currentUser.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        FamilyPhoto photo = familyPhotoRepository
                .findByFamilyPhotoIdAndFamily(familyPhotoId, family)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_PHOTO_NOT_FOUND));

        boolean isUploader = Objects.equals(
                photo.getUser().getUsersId(),
                currentUser.getUsersId()
        );

        boolean uploaderStillInFamily =
                photo.getUser().getFamily() != null && Objects.equals(
                        photo.getUser().getFamily().getFamilyId(),
                        family.getFamilyId()
                );

        boolean isPrimaryManager =
                currentUser.getManagerType() == ManagerType.PRIMARY;

        boolean canDelete =
                isUploader || (!uploaderStillInFamily && isPrimaryManager);

        if (!canDelete) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_DELETE_FORBIDDEN);
        }

        s3Service.delete(photo.getImageKey());
        familyPhotoRepository.delete(photo);

    }
}
