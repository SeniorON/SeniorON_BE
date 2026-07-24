package com.example.senioron.domain.senior.service;

import com.example.senioron.domain.senior.dto.request.SeniorCreateRequest;
import com.example.senioron.domain.senior.dto.request.SeniorRelationUpdateRequest;
import com.example.senioron.domain.senior.dto.response.SeniorCreateResponse;
import com.example.senioron.domain.senior.dto.response.SeniorRelationUpdateResponse;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.UserSenior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.UserSeniorRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeniorService {

    private final SeniorRepository seniorRepository;
    private final UserSeniorRepository userSeniorRepository;

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

        if (seniorRepository.findFirstByFamily(user.getFamily()).isPresent()) {
            throw new BusinessException(ErrorCode.SENIOR_ALREADY_EXISTS);
        }

        Senior senior = Senior.builder()
                .name(request.name())
                .birth(request.birth())
                .phoneNumber(normalizePhoneNumber(request.phoneNumber()))
                .address(request.address())
                .detailAddress(request.detailAddress())
                .family(user.getFamily())
                .registeredBy(user)
                .build();

        Senior savedSenior = seniorRepository.save(senior);
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

    @Transactional
    public SeniorRelationUpdateResponse updateSeniorRelation(
            User user,
            Long seniorId,
            SeniorRelationUpdateRequest request
    ) {
        if (user.getFamily() == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_CONNECTED);
        }

        validateCustomRelation(request.relation(), request.customRelation());

        Senior senior = seniorRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_NOT_FOUND));

        validateSameFamily(user, senior);

        String resolvedCustomRelation =
                resolveCustomRelation(
                        request.relation(),
                        request.customRelation()
                );

        UserSenior userSenior = userSeniorRepository.findByUserAndSenior(user, senior)
                .orElseGet(() -> UserSenior.builder()
                        .user(user)
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
}
