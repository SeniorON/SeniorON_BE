package com.example.senioron.domain.senior.service;

import com.example.senioron.domain.senior.dto.request.SeniorCreateRequest;
import com.example.senioron.domain.senior.dto.response.SeniorCreateResponse;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.repository.SeniorRepository;
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

    /**
     * 로그인한 사용자가 관리할 시니어 정보를 등록합니다.
     */
    @Transactional
    public SeniorCreateResponse createSenior(
            User user,
            SeniorCreateRequest request
    ) {
        validateCustomRelation(request);

        Senior senior = Senior.builder()
                .name(request.name())
                .relation(request.relation())
                .customRelation(resolveCustomRelation(request))
                .birth(request.birth())
                .phoneNumber(normalizePhoneNumber(request.phoneNumber()))
                .address(request.address())
                .detailAddress(request.detailAddress())
                .registeredBy(user)
                .build();

        Senior savedSenior = seniorRepository.save(senior);

        return SeniorCreateResponse.from(savedSenior);
    }

    /**
     * 직접 작성 관계를 선택한 경우 관계명이 입력되었는지 검증합니다.
     */
    private void validateCustomRelation(SeniorCreateRequest request) {
        if (request.relation() == SeniorRelation.OTHER
                && (request.customRelation() == null
                || request.customRelation().isBlank())) {

            throw new BusinessException(
                    ErrorCode.CUSTOM_RELATION_REQUIRED
            );
        }
    }

    /**
     * OTHER가 아닐 경우 불필요한 직접 작성 관계는 저장하지 않습니다.
     */
    private String resolveCustomRelation(SeniorCreateRequest request) {
        if (request.relation() != SeniorRelation.OTHER) {
            return null;
        }

        return request.customRelation().trim();
    }

    /**
     * 전화번호에서 하이픈과 공백을 제거합니다.
     */
    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber
                .replace("-", "")
                .replace(" ", "");
    }
}