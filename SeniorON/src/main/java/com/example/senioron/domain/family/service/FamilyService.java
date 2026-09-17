package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.dto.request.FamilyPrimaryManagerUpdateRequest;
import com.example.senioron.domain.family.dto.response.*;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import com.example.senioron.domain.family.dto.request.PhotoGroupConnectRequest;

@Service
@RequiredArgsConstructor
@Transactional
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final FamilyPhotoRepository familyPhotoRepository;
    private final FamilyPhotoPermissionService familyPhotoPermissionService;
    private final FamilyMemberRepository familyMemberRepository;
    private final PhotoGroupRepository photoGroupRepository;
    private final PhotoGroupFamilyRepository photoGroupFamilyRepository;
    private final DeviceService deviceService;
    private final SeniorRepository seniorRepository;

    private static final int RECENT_UPLOADER_COUNT = 3;
    private static final int RECENT_PHOTO_COUNT = 4;

    // 가족 생성 및 시니어 코드 발급 서비스
    public SeniorCodeCreateResponse createFamily(User principal) {
        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.SENIOR_CODE_CREATE_PARENT_FORBIDDEN);
        }

        String seniorCode = generateUniqueSeniorCode();

        Family family = Family.builder()
                .seniorCode(seniorCode)
                .build();

        Family savedFamily = familyRepository.save(family);

        createFamilyMember(user, savedFamily, ManagerType.PRIMARY);
        createDefaultPhotoGroup(savedFamily);

        return SeniorCodeCreateResponse.builder()
                .familyId(savedFamily.getFamilyId())
                .seniorCode(savedFamily.getSeniorCode())
                .build();
    }

    // 중복되지 않는 시니어 코드 생성
    private String generateUniqueSeniorCode() {
        String code;

        do {
            code = generateSeniorCode();
        } while (familyRepository.existsBySeniorCode(code));

        return code;
    }

    // 랜덤 시니어 코드 생성
    private String generateSeniorCode() {
        String raw = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        return raw.substring(0, 4) + "-" + raw.substring(4, 8);
    }

    // 시니어 코드로 가족 참여 메소드
    public FamilyJoinResponse joinFamily(
            User principal,
            FamilyJoinRequest request
    ) {
        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        Family family = familyRepository.findBySeniorCode(request.getSeniorCode())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INVALID_SENIOR_CODE)
                );

        ManagerType managerType;
        if (user.getRole() == Role.CHILD) {
            managerType = ManagerType.SUB;
        } else if (user.getRole() == Role.PARENT) {
            managerType = ManagerType.NONE;

            deviceService.reconnectDevice(user);
        } else {
            managerType = ManagerType.NONE;
        }

        createFamilyMember(user, family, managerType);
        if (user.getRole() == Role.PARENT) {
            linkParentUserToFamilySenior(user, family);
        }

        return FamilyJoinResponse.builder()
                .familyId(family.getFamilyId())
                .seniorCode(family.getSeniorCode())
                .build();
    }

    // 가족 구성원 조회 메소드
    @Transactional(readOnly=true)
    public List<FamilyMemberResponse> getFamilyMembers(User user, Long seniorId){
        Family family = resolveAccessibleFamily(user, seniorId);

        return familyMemberRepository.findAllByFamilyOrderByIdAsc(family).stream()
                // 계정 주인만 맨 앞 정렬
                .sorted(Comparator.comparing(
                        member -> !Objects.equals(member.getUser().getUsersId(),user.getUsersId())
                ))
                .map(member -> toFamilyMemberResponse(member, user))
                .toList();
    }

    // 주 담당자 변경 메서드
    @Transactional
    public FamilyPrimaryManagerUpdateResponse updatePrimaryManager(
            User principal,
            Long seniorId,
            FamilyPrimaryManagerUpdateRequest request
    ){
        // 현재 로그인한 사용자가 실제 DB에 없는 경우
        User currentUser = userRepository.findByIdForUpdate(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Family family = resolveAccessibleFamily(currentUser, seniorId);

        FamilyMember currentMember = familyMemberRepository
                .findByUserAndFamilyForUpdate(currentUser, family)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND));

        if(currentMember.getManagerType() != ManagerType.PRIMARY){
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 요청으로 받은 대상 사용자 ID가 실제 DB에 없는 경우
        User targetUser = userRepository.findByIdForUpdate(request.getTargetUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        FamilyMember targetMember = familyMemberRepository
                .findByUserAndFamilyForUpdate(targetUser, family)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        if (targetUser.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.PRIMARY_MANAGER_MUST_BE_CHILD);
        }

        // 자신은 주 담당자 변경 대상 불가
        if (Objects.equals(currentUser.getUsersId(), targetUser.getUsersId())) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_PRIMARY_TO_SELF);
        }

        currentMember.updateManagerType(ManagerType.SUB);
        targetMember.updateManagerType(ManagerType.PRIMARY);

        return FamilyPrimaryManagerUpdateResponse.builder()
                .usersId((targetUser.getUsersId()))
                .name(targetUser.getName())
                .managerType(targetMember.getManagerType())
                .build();
    }

    // 가족 구성원 제거 메서드
    public void removeFamilyMember(User principal, Long seniorId, Long targetUserId) {
        // 로그인한 사용자 조회
        User currentUser = userRepository.findByIdForUpdate(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Family family = resolveAccessibleFamily(currentUser, seniorId);

        // 주 담당자만 가족 구성원을 제외할 수 있음
        FamilyMember currentMember = familyMemberRepository
                .findByUserAndFamilyForUpdate(currentUser, family)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND));

        if (currentMember.getManagerType() != ManagerType.PRIMARY) {
            throw new BusinessException(ErrorCode.FAMILY_MEMBER_REMOVE_FORBIDDEN);
        }

        // 제외할 사용자 조회
        User targetUser = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 대상자가 요청자와 같은 가족인지 확인
        if (!familyMemberRepository.existsByUserAndFamily(targetUser, family)) {
            throw new BusinessException(ErrorCode.FAMILY_MEMBER_NOT_FOUND);
        }

        if (Objects.equals(currentUser.getUsersId(), targetUser.getUsersId())) {
            throw new BusinessException(ErrorCode.CANNOT_REMOVE_SELF);
        }

        familyMemberRepository.deleteByUserAndFamily(targetUser, family);
    }

    private FamilyMemberResponse toFamilyMemberResponse(
            FamilyMember familyMember,
            User currentUser
    ) {
        User member = familyMember.getUser();

        return FamilyMemberResponse.builder()
                .usersId(member.getUsersId())
                .name(member.getName())
                .role(member.getRole())
                .managerType(familyMember.getManagerType())
                .canBecomePrimary(
                        member.getRole() == Role.CHILD
                        && familyMember.getManagerType() != ManagerType.PRIMARY
                )
                .profileImageUrl(
                        s3Service.getFileUrl(member.getProfileImageKey())
                )
                .me(Objects.equals(
                        member.getUsersId(),
                        currentUser.getUsersId()
                ))
                .build();
    }

    private FamilyPhotoItemResponse toFamilyPhotoItemResponse(
            FamilyPhoto photo,
            User currentUser
    ) {
        return FamilyPhotoItemResponse.builder()
                .familyPhotoId(photo.getFamilyPhotoId())
                .imageUrl(s3Service.getFileUrl(photo.getImageKey()))
                .uploaderUserId(photo.getUser().getUsersId())
                .uploaderName(photo.getUser().getName())
                .description(photo.getDescription())
                .canDelete(familyPhotoPermissionService.canDelete(photo, currentUser))
                .createdAt(photo.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public FamilyHomeResponse getFamilyHome(User user) {
        Family family = user.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        // 가족 구성원 조회
        List<FamilyMember> familyMembers = familyMemberRepository
                .findAllByFamilyOrderByIdAsc(family);

        // 최신 가족 사진 4개 조회
        List<FamilyPhoto> recentPhotoEntities =
                familyPhotoRepository.findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
                        family,
                        PageRequest.of(0, RECENT_PHOTO_COUNT)
                );

        // 전체 가족사진을 기준으로 최근 업로더 3명 조회
        List<User> recentUploaders =
                familyPhotoRepository.findRecentUploaders(
                        family,
                        PageRequest.of(0, RECENT_UPLOADER_COUNT)
                );

        // 가족 구성원 응답 생성
        List<FamilyMemberResponse> members = familyMembers.stream()
                .sorted(Comparator.comparing(member -> !Objects.equals(
                                member.getUser().getUsersId(),
                                user.getUsersId()
                        )
                ))
                .map(member -> toFamilyMemberResponse(member, user))
                .toList();

        // 최근 업로더 프로필 최대 3개 생성
        List<String> recentUploaderProfileImageUrls =
                recentUploaders.stream()
                        .map(User::getProfileImageKey)
                        .map(profileImageKey -> profileImageKey == null
                                ? null
                                : s3Service.getFileUrl(profileImageKey))
                        .toList();

        List<FamilyPhotoItemResponse> recentPhotos =
                recentPhotoEntities.stream()
                        .map(photo -> toFamilyPhotoItemResponse(photo, user))
                        .toList();

        return FamilyHomeResponse.builder()
                .members(members)
                .recentUploaderProfileImageUrls(
                        recentUploaderProfileImageUrls
                )
                .recentPhotos(recentPhotos)
                .build();
    }

    @Transactional(readOnly = true)
    public SeniorCodeResponse getSeniorCode(
            User principal,
            Long seniorId
    ) {
        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Family family = resolveAccessibleFamily(user, seniorId);

        long familyMemberCount = familyMemberRepository.countByFamily(family);

        return SeniorCodeResponse.builder()
                .seniorCode(family.getSeniorCode())
                .familyMemberCount(familyMemberCount)
                .build();
    }

    private void createFamilyMember(
            User user,
            Family family,
            ManagerType managerType
    ) {
        if (familyMemberRepository.existsByUserAndFamily(user, family)) {
            return;
        }

        familyMemberRepository.save(
                FamilyMember.builder()
                        .user(user)
                        .family(family)
                        .managerType(managerType)
                        .build()
        );
    }

    private void createDefaultPhotoGroup(Family family) {
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
    }

    private void linkParentUserToFamilySenior(User parentUser, Family family) {
        List<Senior> seniors =
                seniorRepository.findAllByFamilyOrderBySeniorIdAscForUpdate(family);

        if (seniors.isEmpty()) {
            return;
        }

        Senior senior = seniors.get(0);
        if (senior.getParentUser() != null) {
            if (Objects.equals(
                    senior.getParentUser().getUsersId(),
                    parentUser.getUsersId()
            )) {
                return;
            }

            throw new BusinessException(ErrorCode.SENIOR_ALREADY_LINKED_TO_PARENT);
        }

        if (seniorRepository.existsByParentUser(parentUser)) {
            throw new BusinessException(ErrorCode.PARENT_USER_ALREADY_LINKED_TO_SENIOR);
        }

        senior.linkParentUser(parentUser);

        try {
            seniorRepository.saveAndFlush(senior);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PARENT_USER_ALREADY_LINKED_TO_SENIOR);
        }
    }

    private Family resolveAccessibleFamily(
            User user,
            Long seniorId
    ) {
        Senior senior = seniorRepository.findById(seniorId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.SENIOR_NOT_FOUND)
                );

        Family family = senior.getFamily();

        if (!familyMemberRepository.existsByUserAndFamily(user, family)) {
            throw new BusinessException(
                    ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED
            );
        }

        return family;
    }

    public void connectPhotoGroup(
            User principal,
            PhotoGroupConnectRequest request
    ) {
        User currentUser = userRepository.findById(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        Family currentFamily = resolveAccessibleFamily(
                currentUser,
                request.getSeniorId()
        );

        FamilyMember currentMember = familyMemberRepository
                .findByUserAndFamilyForUpdate(currentUser, currentFamily)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.FAMILY_NOT_FOUND)
                );

        if (currentMember.getManagerType() != ManagerType.PRIMARY) {
            throw new BusinessException(
                    ErrorCode.PHOTO_GROUP_CONNECTION_FORBIDDEN
            );
        }

        Family targetFamily = familyRepository
                .findBySeniorCode(request.getSeniorCode().trim().toUpperCase())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INVALID_SENIOR_CODE)
                );

        seniorRepository.findFirstByFamilyOrderBySeniorIdAsc(targetFamily)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.SENIOR_NOT_FOUND)
                );

        if (Objects.equals(
                currentFamily.getFamilyId(),
                targetFamily.getFamilyId()
        )) {
            throw new BusinessException(
                    ErrorCode.PHOTO_GROUP_SELF_CONNECTION_NOT_ALLOWED
            );
        }

        if (photoGroupFamilyRepository.existsSharedPhotoGroup(
                currentFamily,
                targetFamily
        )) {
            throw new BusinessException(
                    ErrorCode.PHOTO_GROUP_ALREADY_CONNECTED
            );
        }

        PhotoGroup photoGroup = photoGroupRepository.save(
                PhotoGroup.builder()
                        .name(
                                "Shared Family "
                                        + currentFamily.getFamilyId()
                                        + "-"
                                        + targetFamily.getFamilyId()
                        )
                        .build()
        );

        photoGroupFamilyRepository.saveAll(
                List.of(
                        PhotoGroupFamily.builder()
                                .family(currentFamily)
                                .photoGroup(photoGroup)
                                .build(),
                        PhotoGroupFamily.builder()
                                .family(targetFamily)
                                .photoGroup(photoGroup)
                                .build()
                )
        );
    }

    @Transactional(readOnly = true)
    public List<ConnectedSeniorResponse> getConnectedSeniors(
            User principal,
            Long seniorId
    ) {
        User currentUser = userRepository.findById(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        Family currentFamily = resolveAccessibleFamily(
                currentUser,
                seniorId
        );

        return photoGroupFamilyRepository
                .findConnectedFamilyLinks(currentFamily)
                .stream()
                .map(this::toConnectedSeniorResponse)
                .toList();
    }

    private ConnectedSeniorResponse toConnectedSeniorResponse(
            PhotoGroupFamily connectedLink
    ) {
        Senior connectedSenior = Optional
                .ofNullable(connectedLink.getFamily().getSenior())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.SENIOR_NOT_FOUND)
                );

        return ConnectedSeniorResponse.from(
                connectedLink.getPhotoGroup(),
                connectedSenior
        );
    }
}
