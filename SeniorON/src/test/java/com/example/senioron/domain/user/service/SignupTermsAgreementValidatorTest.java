package com.example.senioron.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.junit.jupiter.api.Test;

class SignupTermsAgreementValidatorTest {

    @Test
    void allowsOptionalHealthAndLocationTermsForChild() {
        assertThatCode(() -> SignupTermsAgreementValidator.validate(
                Role.CHILD, true, true, true, false, true
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrFalseRequiredFamilyInfoProvisionTerm() {
        assertRequiredTermsRejected(Role.CHILD, false, false);
        assertRequiredTermsRejected(Role.CHILD, false, null);
    }

    @Test
    void rejectsMissingExistingRequiredTerm() {
        assertThatThrownBy(() -> SignupTermsAgreementValidator.validate(
                Role.CHILD, null, true, true, false, true
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
    }

    @Test
    void requiresLocationTermForParentOnly() {
        assertRequiredTermsRejected(Role.PARENT, false, true);
        assertRequiredTermsRejected(Role.PARENT, null, true);

        assertThatCode(() -> SignupTermsAgreementValidator.validate(
                Role.PARENT, true, true, true, true, true
        )).doesNotThrowAnyException();
    }

    private void assertRequiredTermsRejected(Role role, Boolean locationInfoAgreed, Boolean familyInfoProvisionAgreed) {
        assertThatThrownBy(() -> SignupTermsAgreementValidator.validate(
                role, true, true, true, locationInfoAgreed, familyInfoProvisionAgreed
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
    }
}
