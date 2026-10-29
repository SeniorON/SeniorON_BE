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

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;

import java.util.List;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
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
    private final PhotoGroupFamilyRepository photoGroupFamilyRepository;
    private final FamilyPhotoSeniorRepository familyPhotoSeniorRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FamilyPhoto create(
            Long userId,
            String imageKey,
            String thumbnailKey,
            String idempotencyKey,
            String description,
            Long familyId,
            List<Long> seniorIds
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

        List<Long> distinctIds = seniorIds.stream()
                .distinct()
                .sorted()
                .toList();

        if (distinctIds.isEmpty()) {
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

        if (member.getManagerType() != ManagerType.PRIMARY
                && member.getManagerType() != ManagerType.SUB) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN);
        }

        // 사용자 행 잠금 이후 공유 대상 권한을 다시 확인한다.
        Map<Long, Senior> targetSeniors = familyMemberRepository
                .findPhotoShareTargetMembers(
                        user,
                        List.of(ManagerType.PRIMARY, ManagerType.SUB)
                )
                .stream()
                .map(target -> target.getFamily().getSenior())
                .collect(Collectors.toMap(Senior::getSeniorId, senior -> senior));

        if (!targetSeniors.keySet().containsAll(distinctIds)) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN);
        }

        List<Senior> seniors = distinctIds.stream()
                .map(targetSeniors::get)
                .toList();

        // 조회가 시니어 기준으로 바뀌기 전까지 기존 그룹 매핑도 함께 저장한다.
        List<PhotoGroup> photoGroups = seniors.stream()
                .map(senior -> photoGroupFamilyRepository
                        .findFirstByFamilyOrderByIdAsc(senior.getFamily())
                        .map(PhotoGroupFamily::getPhotoGroup)
                        .orElseThrow(() ->
                                new BusinessException(ErrorCode.PHOTO_GROUP_NOT_FOUND)
                        )
                )
                .toList();

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

        List<FamilyPhotoSenior> shareTargets =
                seniors.stream()
                        .map(senior ->
                                FamilyPhotoSenior.builder()
                                        .familyPhoto(savedPhoto)
                                        .senior(senior)
                                        .build()
                        )
                        .toList();

        familyPhotoSeniorRepository.saveAllAndFlush(shareTargets);

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
