package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.entity.SignupEmailVerificationCode;
import com.example.senioron.domain.user.repository.SignupEmailVerificationCodeRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupEmailVerificationCodeIssuer {

    private final SignupEmailVerificationCodeRepository signupEmailVerificationCodeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SignupEmailVerificationCode createOrReissue(
            String email,
            String codeHash,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        return signupEmailVerificationCodeRepository.findByEmail(email)
                .map(existingCode -> {
                    existingCode.reissue(codeHash, issuedAt, expiresAt);
                    return existingCode;
                })
                .orElseGet(() -> signupEmailVerificationCodeRepository.saveAndFlush(
                        SignupEmailVerificationCode.builder()
                                .email(email)
                                .codeHash(codeHash)
                                .issuedAt(issuedAt)
                                .expiresAt(expiresAt)
                                .build()
                ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SignupEmailVerificationCode reissueExisting(
            String email,
            String codeHash,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        SignupEmailVerificationCode existingCode =
                signupEmailVerificationCodeRepository.findByEmail(email)
                        .orElseThrow();

        existingCode.reissue(codeHash, issuedAt, expiresAt);

        return existingCode;
    }
}
