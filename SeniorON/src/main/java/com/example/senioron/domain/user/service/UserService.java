package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.dto.request.SignupEmailVerificationCodeSendRequest;
import com.example.senioron.domain.user.dto.request.UserLoginRequest;
import com.example.senioron.domain.user.dto.request.UserRoleUpdateRequest;
import com.example.senioron.domain.user.dto.request.UserSignUpRequest;
import com.example.senioron.domain.user.dto.response.*;
import com.example.senioron.domain.user.entity.SignupEmailVerificationCode;
import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.inactivity.service.InactivitySettingService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.event.SignupEmailVerificationCodeSendEvent;
import com.example.senioron.domain.user.repository.SignupEmailVerificationCodeRepository;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private static final int VERIFICATION_CODE_BOUND = 1_000_000;
    private static final int VERIFICATION_CODE_EXPIRATION_MINUTES = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final SignupEmailVerificationCodeRepository signupEmailVerificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final InactivitySettingService inactivitySettingService;
    private final DeviceService deviceService;
    private final ApplicationEventPublisher eventPublisher;

    // 아이디 중복 확인 서비스
    public LoginIdCheckResponse checkLoginId(String loginId) {
        boolean exists = userRepository.existsByLoginId(loginId);

        return LoginIdCheckResponse.builder()
                .isAvailable(!exists)
                .build();
    }


    // 최종 회원가입 서비스
    public UserSignUpResponse signUp(UserSignUpRequest request) {
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (!request.getPassword().equals(request.getPasswordCheck())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        User user = User.builder()
                .loginId(request.getLoginId())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .birth(request.getBirth())
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        inactivitySettingService.createDefaultSetting(savedUser);

        return UserSignUpResponse.builder()
                .usersId(savedUser.getUsersId())
                .name(savedUser.getName())
                .loginId(savedUser.getLoginId())
                .build();
    }

    public SignupEmailVerificationCodeSendResponse sendSignupEmailVerificationCode(
            SignupEmailVerificationCodeSendRequest request
    ) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        LocalDateTime now = LocalDateTime.now();
        String verificationCode = generateVerificationCode();
        String codeHash = passwordEncoder.encode(verificationCode);

        SignupEmailVerificationCode savedCode = signupEmailVerificationCodeRepository.findByEmail(request.getEmail())
                .map(existingCode -> {
                    existingCode.reissue(
                            codeHash,
                            now,
                            now.plusMinutes(VERIFICATION_CODE_EXPIRATION_MINUTES)
                    );
                    return existingCode;
                })
                .orElseGet(() -> signupEmailVerificationCodeRepository.save(
                        SignupEmailVerificationCode.builder()
                                .email(request.getEmail())
                                .codeHash(codeHash)
                                .issuedAt(now)
                                .expiresAt(now.plusMinutes(VERIFICATION_CODE_EXPIRATION_MINUTES))
                                .build()
                ));

        eventPublisher.publishEvent(new SignupEmailVerificationCodeSendEvent(
                savedCode.getEmail(),
                verificationCode
        ));

        return SignupEmailVerificationCodeSendResponse.builder()
                .sent(true)
                .verificationId(savedCode.getSignupEmailVerificationCodeId())
                .build();
    }


    // 로그인 서비스
    public UserLoginResponse login(UserLoginRequest request) {
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        if(request.getFcmToken() != null && !request.getFcmToken().isBlank()){
            deviceService.registerToken(user, request.getFcmToken(), request.getDeviceIdentifier());
        }

        String accessToken = jwtUtil.createAccessToken(user);

        return UserLoginResponse.builder()
                .usersId(user.getUsersId())
                .name(user.getName())
                .loginId(user.getLoginId())
                .accessToken(accessToken)
                .build();
    }

    // 계정의 역할 수정 서비스
    public UserRoleUpdateResponse updateRole(
            User principal,
            UserRoleUpdateRequest request
    ) {
        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        System.out.println("=== 역할 변경 전 ===");
        System.out.println("userId = " + user.getUsersId());
        System.out.println("role = " + user.getRole());

        user.updateRole(request.getRole());

        System.out.println("=== 역할 변경 후 ===");
        System.out.println("userId = " + user.getUsersId());
        System.out.println("role = " + user.getRole());

        return UserRoleUpdateResponse.builder()
                .usersId(user.getUsersId())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }

    private String generateVerificationCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(VERIFICATION_CODE_BOUND));
    }
}
