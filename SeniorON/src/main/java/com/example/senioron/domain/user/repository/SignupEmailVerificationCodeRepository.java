package com.example.senioron.domain.user.repository;

import com.example.senioron.domain.user.entity.SignupEmailVerificationCode;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface SignupEmailVerificationCodeRepository
        extends JpaRepository<SignupEmailVerificationCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SignupEmailVerificationCode> findByEmail(String email);
}
