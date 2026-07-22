package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.dto.request.FamilyPrimaryManagerUpdateRequest;
import com.example.senioron.domain.family.dto.response.*;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final FamilyPhotoRepository familyPhotoRepository;

    private static final int RECENT_UPLOADER_COUNT = 3;
    private static final int RECENT_PHOTO_COUNT = 4;

    // 가족 생성 및 공유코드 발급 서비스
    public FamilyCodeCreateResponse createFamily(User principal) {
        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String familyCode = generateUniqueFamilyCode();

        Family family = Family.builder()
                .familyCode(familyCode)
                .build();

        Family savedFamily = familyRepository.save(family);

        user.updateFamily(savedFamily);
        user.updateManagerType(ManagerType.PRIMARY);

        return FamilyCodeCreateResponse.builder()
                .familyId(savedFamily.getFamilyId())
                .familyCode(savedFamily.getFamilyCode())
                .build();
    }

    // 중복되지 않는 가족 공유코드 생성
    private String generateUniqueFamilyCode() {
        String code;

        do {
            code = generateFamilyCode();
        } while (familyRepository.existsByFamilyCode(code));

        return code;
    }

    // 랜덤 가족 공유코드 생성
    private String generateFamilyCode() {
        String raw = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        return raw.substring(0, 4) + "-" + raw.substring(4, 8);
    }

    // 공유코드로 가족 참여 메소드
    public FamilyJoinResponse joinFamily(
            User principal,
            FamilyJoinRequest request
    ) {
        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        Family family = familyRepository.findByFamilyCode(request.getFamilyCode())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INVALID_FAMILY_CODE)
                );

        user.updateFamily(family);
        if (user.getRole() == Role.CHILD) {
            user.updateManagerType(ManagerType.SUB);
        } else if (user.getRole() == Role.PARENT) {
            user.updateManagerType(ManagerType.NONE);
        }

        return FamilyJoinResponse.builder()
                .familyId(family.getFamilyId())
                .familyCode(family.getFamilyCode())
                .build();
    }

    // 가족 구성원 조회 메소드
    @Transactional(readOnly=true)
    public List<FamilyMemberResponse> getFamilyMembers(User user){
        Family family = user.getFamily();

        if(family == null){
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        return userRepository.findAllByFamily(family).stream()
                // 계정 주인만 맨 앞 정렬
                .sorted(Comparator.comparing(
                        member -> !Objects.equals(member.getUsersId(),user.getUsersId())
                ))
                .map(member -> toFamilyMemberResponse(member, user))
                .toList();
    }

    // 주 담당자 변경 메서드
    @Transactional
    public FamilyPrimaryManagerUpdateResponse updatePrimaryManager(
            User principal,
            FamilyPrimaryManagerUpdateRequest request
    ){
        // 현재 로그인한 사용자가 실제 DB에 없는 경우
        User currentUser = userRepository.findByIdForUpdate(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Family family = currentUser.getFamily();

        if(family == null){
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        if(currentUser.getManagerType() != ManagerType.PRIMARY){
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 요청으로 받은 대상 사용자 ID가 실제 DB에 없는 경우
        User targetUser = userRepository.findByIdForUpdate(request.getTargetUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(targetUser.getFamily() == null || !Objects.equals(family.getFamilyId(), targetUser.getFamily().getFamilyId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (targetUser.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.PRIMARY_MANAGER_MUST_BE_CHILD);
        }

        // 자신은 주 담당자 변경 대상 불가
        if (Objects.equals(currentUser.getUsersId(), targetUser.getUsersId())) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_PRIMARY_TO_SELF);
        }

        currentUser.updateManagerType(ManagerType.SUB);
        targetUser.updateManagerType(ManagerType.PRIMARY);

        return FamilyPrimaryManagerUpdateResponse.builder()
                .usersId((targetUser.getUsersId()))
                .name(targetUser.getName())
                .managerType(targetUser.getManagerType())
                .build();
    }

    // 가족 구성원 제거 메서드
    public void removeFamilyMember(User principal, Long targetUserId) {
        // 로그인한 사용자 조회
        User currentUser = userRepository.findByIdForUpdate(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 요청자가 가족에 소속되어 있는지 확인
        Family family = currentUser.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        // 주 담당자만 가족 구성원을 제외할 수 있음
        if (currentUser.getManagerType() != ManagerType.PRIMARY) {
            throw new BusinessException(ErrorCode.FAMILY_MEMBER_REMOVE_FORBIDDEN);
        }

        // 제외할 사용자 조회
        User targetUser = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 대상자가 요청자와 같은 가족인지 확인
        if (targetUser.getFamily() == null || !Objects.equals(family.getFamilyId(), targetUser.getFamily().getFamilyId())) {
            throw new BusinessException(ErrorCode.FAMILY_MEMBER_NOT_FOUND);
        }

        if (Objects.equals(currentUser.getUsersId(), targetUser.getUsersId())) {
            throw new BusinessException(ErrorCode.CANNOT_REMOVE_SELF);
        }

        targetUser.removeFromFamily();

    }

    private FamilyMemberResponse toFamilyMemberResponse(
            User member,
            User currentUser
    ) {
        return FamilyMemberResponse.builder()
                .usersId(member.getUsersId())
                .name(member.getName())
                .managerType(member.getManagerType())
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
            FamilyPhoto photo
    ) {
        return FamilyPhotoItemResponse.builder()
                .familyPhotoId(photo.getFamilyPhotoId())
                .imageUrl(s3Service.getFileUrl(photo.getImageKey()))
                .uploaderName(photo.getUser().getName())
                .description(photo.getDescription())
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
        List<User> familyMembers = userRepository.findAllByFamily(family);

        // 최신 가족 사진 4개 조회
        List<FamilyPhoto> recentPhotoEntities =
                familyPhotoRepository.findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
                        family,
                        PageRequest.of(0, RECENT_PHOTO_COUNT)
                );

        // 가족 구성원 응답 생성
        List<FamilyMemberResponse> members = familyMembers.stream()
                .sorted(Comparator.comparing(member -> !Objects.equals(
                                member.getUsersId(),
                                user.getUsersId()
                        )
                ))
                .map(member -> toFamilyMemberResponse(member, user))
                .toList();

        // 현재 가족 구성원을 ID로 찾을 수 있게 변환
        Map<Long, User> memberById = familyMembers.stream()
                .collect(Collectors.toMap(
                        User::getUsersId,
                        member -> member
                ));

        // 사진 업로더를 최근 업로드 순으로 중복 없이 저장
        Map<Long, User> recentUploaderById = new LinkedHashMap<>();

        recentPhotoEntities.forEach(photo -> {
                    Long uploaderId = photo.getUser().getUsersId();
                    User uploader = memberById.get(uploaderId);

                    // 현재 가족 구성원인 업로더만 포함
                    if (uploader != null) {
                        recentUploaderById.putIfAbsent(uploaderId, uploader);
                    }
                });

        // 최근 업로더 프로필 최대 3개 생성
        List<String> recentUploaderProfileImageUrls =
                recentUploaderById.values().stream()
                        .limit(RECENT_UPLOADER_COUNT)
                        .map(User::getProfileImageKey)
                        .map(profileImageKey -> profileImageKey == null
                                ? null
                                : s3Service.getFileUrl(profileImageKey))
                        .toList();

        // 최신 사진 응답 생성
        List<FamilyPhotoItemResponse> recentPhotos =
                recentPhotoEntities.stream()
                        .map(this::toFamilyPhotoItemResponse)
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
    public FamilyCodeResponse getFamilyCode(User user) {
        Family family = user.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        long familyMemberCount = userRepository.countByFamily(family);

        return FamilyCodeResponse.builder()
                .familyCode(family.getFamilyCode())
                .familyMemberCount(familyMemberCount)
                .build();
    }
}
