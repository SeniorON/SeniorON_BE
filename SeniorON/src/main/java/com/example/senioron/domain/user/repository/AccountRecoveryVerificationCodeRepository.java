package com.example.senioron.domain.user.repository;

import com.example.senioron.domain.user.entity.AccountRecoveryPurpose;
import com.example.senioron.domain.user.entity.AccountRecoveryVerificationCode;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRecoveryVerificationCodeRepository
        extends JpaRepository<AccountRecoveryVerificationCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountRecoveryVerificationCode> findByAccountRecoveryVerificationCodeIdAndPurposeAndUsedAtIsNull(
            Long accountRecoveryVerificationCodeId,
            AccountRecoveryPurpose purpose
    );

    @Query("""
            UPDATE AccountRecoveryVerificationCode code
            SET code.usedAt = :expiredAt
            WHERE code.user = :user
              AND code.purpose = :purpose
              AND code.usedAt IS NULL
            """)
    @Modifying(clearAutomatically = true)
    void expireAllByUserAndPurposeAndUsedAtIsNull(
            @Param("user") User user,
            @Param("purpose") AccountRecoveryPurpose purpose,
            @Param("expiredAt") LocalDateTime expiredAt
    );

}
