package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;

/**
 * 일반/소셜 회원가입에서 공통으로 사용하는 가입 약관 검증기입니다.
 *
 * 건강정보 동의는 회원가입 시 선택 사항입니다. 이후 복약·병원 정보의 최초 등록 시에는
 * 별도의 동의 절차를 통해 다시 검증해야 하며, 이 값으로 그 절차를 대체하지 않습니다.
 */
public final class SignupTermsAgreementValidator {

    private SignupTermsAgreementValidator() {
    }

    public static void validate(
            Role role,
            Boolean serviceTermsAgreed,
            Boolean privacyPolicyAgreed,
            Boolean ageOver14Agreed,
            Boolean locationInfoAgreed,
            Boolean familyInfoProvisionAgreed
    ) {
        boolean requiredTermsAgreed = Boolean.TRUE.equals(serviceTermsAgreed)
                && Boolean.TRUE.equals(privacyPolicyAgreed)
                && Boolean.TRUE.equals(ageOver14Agreed)
                && Boolean.TRUE.equals(familyInfoProvisionAgreed);

        if (!requiredTermsAgreed) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }

        if (role == Role.PARENT) {
            validateParentLocationInfo(locationInfoAgreed);
        }
    }

    public static void validateParentLocationInfo(Boolean locationInfoAgreed) {
        if (!Boolean.TRUE.equals(locationInfoAgreed)) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
    }
}
