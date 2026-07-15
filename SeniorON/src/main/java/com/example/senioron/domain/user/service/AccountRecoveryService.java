package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.dto.request.LoginIdFindRequest;
import com.example.senioron.domain.user.dto.request.PasswordResetRequest;
import com.example.senioron.domain.user.dto.request.PasswordResetCodeSendRequest;
import com.example.senioron.domain.user.dto.request.PasswordResetCodeVerifyRequest;
import com.example.senioron.domain.user.dto.response.LoginIdFindResponse;
import com.example.senioron.domain.user.dto.response.PasswordResetResponse;
import com.example.senioron.domain.user.dto.response.PasswordResetCodeSendResponse;
import com.example.senioron.domain.user.dto.response.PasswordResetCodeVerifyResponse;
import com.example.senioron.domain.user.entity.AccountRecoveryPurpose;
import com.example.senioron.domain.user.entity.AccountRecoveryVerificationCode;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.AccountRecoveryVerificationCodeRepository;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.mail.EmailService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountRecoveryService {

    private static final int VERIFICATION_CODE_BOUND = 1_000_000;
    private static final int VERIFICATION_CODE_EXPIRATION_MINUTES = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AccountRecoveryVerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public LoginIdFindResponse findLoginId(LoginIdFindRequest request) {
        User user = userRepository.findByNameAndEmail(request.getName(), request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_ID_NOT_FOUND));

        return LoginIdFindResponse.builder()
                .loginId(user.getLoginId())
                .build();
    }

    @Transactional
    public PasswordResetCodeSendResponse sendPasswordResetVerificationCode(
            PasswordResetCodeSendRequest request
    ) {
        User user = userRepository.findByNameAndLoginId(request.getName(), request.getLoginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_RECOVERY_USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        verificationCodeRepository
                .findTopByUserAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
                        user,
                        AccountRecoveryPurpose.PASSWORD_RESET
                )
                .ifPresent(code -> code.expire(now));

        String verificationCode = generateVerificationCode();
        AccountRecoveryVerificationCode savedCode = AccountRecoveryVerificationCode.builder()
                .user(user)
                .purpose(AccountRecoveryPurpose.PASSWORD_RESET)
                .codeHash(passwordEncoder.encode(verificationCode))
                .expiresAt(now.plusMinutes(VERIFICATION_CODE_EXPIRATION_MINUTES))
                .build();

        verificationCodeRepository.save(savedCode);
        emailService.sendPasswordResetVerificationCode(user.getEmail(), verificationCode);

        return PasswordResetCodeSendResponse.builder()
                .sent(true)
                .verificationId(savedCode.getAccountRecoveryVerificationCodeId())
                .build();
    }

    @Transactional
    public PasswordResetCodeVerifyResponse verifyPasswordResetVerificationCode(
            PasswordResetCodeVerifyRequest request
    ) {
        AccountRecoveryVerificationCode savedCode = verificationCodeRepository
                .findByAccountRecoveryVerificationCodeIdAndPurposeAndUsedAtIsNull(
                        request.getVerificationId(),
                        AccountRecoveryPurpose.PASSWORD_RESET
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE));

        LocalDateTime now = LocalDateTime.now();
        if (savedCode.isExpired(now)
                || !passwordEncoder.matches(request.getVerificationCode(), savedCode.getCodeHash())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE);
        }

        savedCode.verify(now);

        return PasswordResetCodeVerifyResponse.builder()
                .verified(true)
                .build();
    }

    @Transactional
    public PasswordResetResponse resetPassword(PasswordResetRequest request) {
        if (!request.getNewPassword().equals(request.getNewPasswordCheck())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        AccountRecoveryVerificationCode savedCode = verificationCodeRepository
                .findByAccountRecoveryVerificationCodeIdAndPurposeAndUsedAtIsNull(
                        request.getVerificationId(),
                        AccountRecoveryPurpose.PASSWORD_RESET
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE));

        LocalDateTime now = LocalDateTime.now();
        if (savedCode.isExpired(now) || !savedCode.isVerified()) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE);
        }

        User user = savedCode.getUser();
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        savedCode.use(now);

        return PasswordResetResponse.builder()
                .reset(true)
                .build();
    }

    private String generateVerificationCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(VERIFICATION_CODE_BOUND));
    }
}
