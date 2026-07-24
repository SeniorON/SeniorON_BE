package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyPhotoCreateRequest;
import com.example.senioron.domain.family.dto.response.FamilyPhotoCursorResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoListResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyPhotoService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final FamilyPhotoPermissionService familyPhotoPermissionService;

    @Transactional
    public FamilyPhotoItemResponse createPhoto(
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

            return toItemResponse(savedPhoto, user);

        } catch(RuntimeException e) {
            s3Service.delete(imageKey);
            throw e;
        }
    }

    private FamilyPhotoItemResponse toItemResponse(
            FamilyPhoto photo,
            User currentUser
    ){
        return FamilyPhotoItemResponse.builder()
                .familyPhotoId(photo.getFamilyPhotoId())
                .imageUrl(s3Service.getFileUrl(photo.getImageKey()))
                .uploaderUserId(photo.getUser().getUsersId())
                .uploaderName(photo.getUser().getName())
                .description(photo.getDescription())
                .canDelete(
                        familyPhotoPermissionService.canDelete(
                                photo,
                                currentUser
                        )
                )
                .createdAt(photo.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public FamilyPhotoListResponse getPhotos(
            User principal,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("사진 조회 개수는 1~50이어야 합니다.");
        }

        if ((cursorCreatedAt == null) != (cursorId == null)){
            throw new IllegalArgumentException(
                    "cursorCreatedAt과 cursorId는 함께 전달해야 합니다."
            );
        }

        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Family family = user.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        Pageable pageable = PageRequest.of(0, size + 1);

        List<FamilyPhoto> fetchedPhotos;

        if (cursorCreatedAt == null) {
            fetchedPhotos = familyPhotoRepository
                    .findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
                            family,
                            pageable
                    );
        }else{
            fetchedPhotos = familyPhotoRepository.findNextPageByCursor(
                    family,
                    cursorCreatedAt,
                    cursorId,
                    pageable
            );
        }

        boolean hasNext = fetchedPhotos.size() > size;

        List<FamilyPhoto> pagePhotos = hasNext
                ? fetchedPhotos.subList(0, size)
                : fetchedPhotos;

        FamilyPhotoCursorResponse nextCursor = null;

        if (hasNext) {
            FamilyPhoto lastPhoto = pagePhotos.get(pagePhotos.size() - 1);

            nextCursor = FamilyPhotoCursorResponse.builder()
                    .createdAt(lastPhoto.getCreatedAt())
                    .familyPhotoId(lastPhoto.getFamilyPhotoId())
                    .build();
        }

        List<FamilyPhotoItemResponse> photoResponses = pagePhotos.stream()
                .map(photo -> toItemResponse(photo, user))
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

        if (!familyPhotoPermissionService.canDelete(photo, currentUser)) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_DELETE_FORBIDDEN);
        }

        String imageKey = photo.getImageKey();

        familyPhotoRepository.delete(photo);
        registerS3DeleteAfterCommit(imageKey);
    }

    private void registerS3DeleteAfterCommit(String imageKey){
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization(){
                    @Override
                    public void afterCommit(){
                        try{
                            s3Service.delete(imageKey);
                        }catch(RuntimeException e){
                            log.warn(
                                    "가족 사진 S3 삭제 실패, imageKey={}",
                                    imageKey,
                                    e
                            );
                        }
                    }
                }
        );
    }
}
