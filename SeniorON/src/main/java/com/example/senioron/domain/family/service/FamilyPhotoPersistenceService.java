package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.event.FamilyPhotoSharedEvent;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Optional;

import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;

import java.util.List;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.PhotoGroupRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.UserStatus;

@Service
@RequiredArgsConstructor
public class FamilyPhotoPersistenceService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final UserRepository userRepository;
    private final FamilyPhotoGroupRepository familyPhotoGroupRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PhotoGroupRepository photoGroupRepository;
    private final PhotoGroupFamilyRepository photoGroupFamilyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FamilyPhoto create(
            Long userId,
            String imageKey,
            String thumbnailKey,
            String idempotencyKey,
            String description,
            Long familyId,
            List<Long> photoGroupIds
    ) {
        // 구성원 제외·담당자 변경 로직도 이 사용자 행을 잠근다.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        if (user.getRole() != Role.CHILD
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<Long> distinctIds = photoGroupIds.stream()
                .distinct()
                .sorted()
                .toList();

        if (distinctIds.isEmpty()) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN);
        }

        // 다른 권한 조회 전에 그룹을 잠그고 최신 상태를 읽는다.
        List<PhotoGroup> photoGroups =
                photoGroupRepository.findAllByIdsForUpdate(distinctIds);

        if (photoGroups.size() != distinctIds.size()
                || photoGroups.stream().anyMatch(group -> !group.isActive())) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN);
        }

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.FAMILY_NOT_FOUND)
                );

        var member = familyMemberRepository.findByUserAndFamily(user, family)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED)
                );

        List<PhotoGroupFamily> accessibleLinks;

        if (member.getManagerType() == ManagerType.PRIMARY) {
            accessibleLinks = photoGroupFamilyRepository
                    .findAllByFamilyAndPhotoGroupIds(family, distinctIds);
        } else if (member.getManagerType() == ManagerType.SUB) {
            accessibleLinks = photoGroupFamilyRepository
                    .findManagedUploadGroupLinks(family, user, distinctIds);
        } else {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN);
        }

        if (accessibleLinks.size() != distinctIds.size()) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN);
        }

        PhotoGroup representativePhotoGroup = photoGroups.get(0);

        FamilyPhoto familyPhoto = FamilyPhoto.builder()
                .photoGroup(representativePhotoGroup)
                .user(user)
                .imageKey(imageKey)
                .thumbnailKey(thumbnailKey)
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

        eventPublisher.publishEvent(
                new FamilyPhotoSharedEvent(savedPhoto.getFamilyPhotoId())
        );

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean attachThumbnail(Long photoId, String thumbnailKey) {
        if (thumbnailKey == null || thumbnailKey.isBlank()) {
            throw new IllegalArgumentException("썸네일 키가 필요합니다.");
        }

        return familyPhotoRepository.updateThumbnailIfAbsent(
                photoId,
                thumbnailKey
        ) == 1;
    }
}
