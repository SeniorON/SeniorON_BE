package com.example.senioron.domain.senior.service;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.dto.request.SeniorCreateRequest;
import com.example.senioron.domain.senior.dto.request.SeniorParentLinkRequest;
import com.example.senioron.domain.senior.dto.request.SeniorRelationUpdateRequest;
import com.example.senioron.domain.senior.dto.response.ManagedSeniorResponse;
import com.example.senioron.domain.senior.dto.response.SeniorCreateResponse;
import com.example.senioron.domain.senior.dto.response.SeniorFamilyResponse;
import com.example.senioron.domain.senior.dto.response.SeniorParentLinkResponse;
import com.example.senioron.domain.senior.dto.response.SeniorRelationUpdateResponse;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.UserSenior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.UserSeniorRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeniorService {

    private final SeniorRepository seniorRepository;
    private final UserSeniorRepository userSeniorRepository;
    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;

    public List<ManagedSeniorResponse> getManagedSeniors(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }

        return userSeniorRepository.findAllByUserOrderByUserSeniorIdAsc(user)
                .stream()
                .map(ManagedSeniorResponse::from)
                .toList();
    }

    public List<SeniorFamilyResponse> getFamilySeniors(User principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }

        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getFamily() == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_CONNECTED);
        }

        return seniorRepository.findAllByFamilyOrderBySeniorIdAsc(user.getFamily())
                .stream()
                .map(SeniorFamilyResponse::from)
                .toList();
    }

    @Transactional
    public SeniorParentLinkResponse linkParentUser(
            User principal,
            SeniorParentLinkRequest request
    ) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }

        User parentUser = userRepository.findByIdForUpdate(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (parentUser.getRole() != Role.PARENT) {
            throw new BusinessException(ErrorCode.SENIOR_PARENT_LINK_PARENT_ONLY);
        }

        if (parentUser.getFamily() == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_CONNECTED);
        }

        Senior senior = seniorRepository.findByIdForUpdate(request.seniorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_NOT_FOUND));

        validateParentUserSameFamily(parentUser, senior);
        validateSeniorCanLinkParentUser(senior, parentUser);
        validateParentUserCanLinkSenior(parentUser, senior);

        senior.linkParentUser(parentUser);

        try {
            return SeniorParentLinkResponse.from(seniorRepository.saveAndFlush(senior));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PARENT_USER_ALREADY_LINKED_TO_SENIOR);
        }
    }

    /**
     * 로그인한 사용자가 관리할 시니어 정보를 등록합니다.
     */
    @Transactional
    public SeniorCreateResponse createSenior(
            User user,
            SeniorCreateRequest request
    ) {
        if (user.getFamily() == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_CONNECTED);
        }

        validateCustomRelation(request);

        Family lockedFamily = familyRepository.findByIdForUpdate(user.getFamily().getFamilyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND));

        Senior senior = Senior.builder()
                .name(request.name())
                .birth(request.birth())
                .phoneNumber(normalizePhoneNumber(request.phoneNumber()))
                .address(request.address())
                .detailAddress(request.detailAddress())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .family(lockedFamily)
                .registeredBy(user)
                .build();

        Senior savedSenior = saveSeniorOrThrowAlreadyExists(senior);
        UserSenior userSenior = userSeniorRepository.save(
                UserSenior.builder()
                        .user(user)
                        .senior(savedSenior)
                        .relation(request.relation())
                        .customRelation(resolveCustomRelation(request))
                        .build()
        );

        return SeniorCreateResponse.from(savedSenior, userSenior);
    }

    private Senior saveSeniorOrThrowAlreadyExists(Senior senior) {
        try {
            return seniorRepository.saveAndFlush(senior);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SENIOR_ALREADY_EXISTS);
        }
    }

    @Transactional
    public SeniorRelationUpdateResponse updateSeniorRelation(
            User user,
            Long seniorId,
            SeniorRelationUpdateRequest request
    ) {
        User lockedUser = userRepository.findByIdForUpdate(user.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (lockedUser.getFamily() == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_CONNECTED);
        }

        validateCustomRelation(request.relation(), request.customRelation());

        Senior senior = seniorRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_NOT_FOUND));

        validateSameFamily(lockedUser, senior);

        String resolvedCustomRelation =
                resolveCustomRelation(
                        request.relation(),
                        request.customRelation()
                );

        UserSenior userSenior = userSeniorRepository.findByUserAndSenior(lockedUser, senior)
                .orElseGet(() -> UserSenior.builder()
                        .user(lockedUser)
                        .senior(senior)
                        .relation(request.relation())
                        .customRelation(resolvedCustomRelation)
                        .build());

        userSenior.updateRelation(request.relation(), resolvedCustomRelation);

        return SeniorRelationUpdateResponse.from(
                userSeniorRepository.save(userSenior)
        );
    }

    /**
     * 직접 작성 관계를 선택한 경우 관계명이 입력되었는지 검증합니다.
     */
    private void validateCustomRelation(SeniorCreateRequest request) {
        validateCustomRelation(request.relation(), request.customRelation());
    }

    private void validateCustomRelation(SeniorRelation relation, String customRelation) {
        if (relation == SeniorRelation.OTHER
                && (customRelation == null
                || customRelation.isBlank())) {
            throw new BusinessException(
                    ErrorCode.CUSTOM_RELATION_REQUIRED
            );
        }
    }

    /**
     * OTHER가 아닐 경우 불필요한 직접 작성 관계는 저장하지 않습니다.
     */
    private String resolveCustomRelation(SeniorCreateRequest request) {
        return resolveCustomRelation(request.relation(), request.customRelation());
    }

    private String resolveCustomRelation(SeniorRelation relation, String customRelation) {
        if (relation != SeniorRelation.OTHER) {
            return null;
        }

        return customRelation.trim();
    }

    /**
     * 전화번호에서 하이픈과 공백을 제거합니다.
     */
    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber
                .replace("-", "")
                .replace(" ", "");
    }

    private void validateSameFamily(User user, Senior senior) {
        if (senior.getFamily() == null
                || !user.getFamily()
                .getFamilyId()
                .equals(
                        senior.getFamily()
                                .getFamilyId()
                )) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateParentUserSameFamily(User parentUser, Senior senior) {
        if (senior.getFamily() == null
                || !Objects.equals(
                parentUser.getFamily().getFamilyId(),
                senior.getFamily().getFamilyId()
        )) {
            throw new BusinessException(ErrorCode.SENIOR_NOT_IN_USER_FAMILY);
        }
    }

    private void validateSeniorCanLinkParentUser(Senior senior, User parentUser) {
        if (senior.getParentUser() != null
                && !Objects.equals(
                senior.getParentUser().getUsersId(),
                parentUser.getUsersId()
        )) {
            throw new BusinessException(ErrorCode.SENIOR_ALREADY_LINKED_TO_PARENT);
        }
    }

    private void validateParentUserCanLinkSenior(User parentUser, Senior senior) {
        if (senior.getParentUser() != null
                && Objects.equals(
                senior.getParentUser().getUsersId(),
                parentUser.getUsersId()
        )) {
            return;
        }

        if (seniorRepository.existsByParentUser(parentUser)) {
            throw new BusinessException(ErrorCode.PARENT_USER_ALREADY_LINKED_TO_SENIOR);
        }
    }
}
