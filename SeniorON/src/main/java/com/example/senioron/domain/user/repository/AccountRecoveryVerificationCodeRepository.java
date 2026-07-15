package com.example.senioron.domain.user.repository;

import com.example.senioron.domain.user.entity.AccountRecoveryPurpose;
import com.example.senioron.domain.user.entity.AccountRecoveryVerificationCode;
import com.example.senioron.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRecoveryVerificationCodeRepository
        extends JpaRepository<AccountRecoveryVerificationCode, Long> {

    Optional<AccountRecoveryVerificationCode> findByAccountRecoveryVerificationCodeIdAndPurposeAndUsedAtIsNull(
            Long accountRecoveryVerificationCodeId,
            AccountRecoveryPurpose purpose
    );

    Optional<AccountRecoveryVerificationCode> findTopByUserAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            User user,
            AccountRecoveryPurpose purpose
    );
}
