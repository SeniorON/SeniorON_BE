package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.dto.request.SignupEmailVerificationCodeSendRequest;
import com.example.senioron.domain.user.dto.request.SignupEmailVerificationCodeVerifyRequest;
import com.example.senioron.domain.user.dto.request.TokenRefreshRequest;
import com.example.senioron.domain.user.dto.request.UserLoginRequest;
import com.example.senioron.domain.user.dto.request.UserRoleUpdateRequest;
import com.example.senioron.domain.user.dto.request.UserSignUpRequest;
import com.example.senioron.domain.user.dto.request.UserWithdrawalRequest;
import com.example.senioron.domain.user.dto.response.*;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.UserSenior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.SignupEmailVerificationCode;
import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.inactivity.service.InactivitySettingService;
import com.example.senioron.domain.senior.repository.UserSeniorRepository;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.event.SignupEmailVerificationCodeSendEvent;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.domain.user.repository.SignupEmailVerificationCodeRepository;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private static final int VERIFICATION_CODE_BOUND = 1_000_000;
    private static final int VERIFICATION_CODE_EXPIRATION_MINUTES = 5;
    public static final String WITHDRAWAL_CONFIRMATION = "회원 탈퇴";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SignupEmailVerificationCodeRepository signupEmailVerificationCodeRepository;
    private final SignupEmailVerificationCodeIssuer signupEmailVerificationCodeIssuer;
    private final SocialAccountRepository socialAccountRepository;
    private final RefreshTokenService refreshTokenService;
    private final DeviceRepository deviceRepository;
    private final SeniorRepository seniorRepository;
    private final UserSeniorRepository userSeniorRepository;
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

        SignupEmailVerificationCode verificationCode = signupEmailVerificationCodeRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_EMAIL_VERIFICATION_CODE_NOT_FOUND));
        validateSignupEmailVerified(verificationCode, LocalDateTime.now());

        User user = User.builder()
                .loginId(request.getLoginId())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .birth(request.getBirth())
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        inactivitySettingService.createDefaultSetting(savedUser);
        signupEmailVerificationCodeRepository.delete(verificationCode);

        return UserSignUpResponse.builder()
                .usersId(savedUser.getUsersId())
                .name(savedUser.getName())
                .loginId(savedUser.getLoginId())
                .role(savedUser.getRole())
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

        LocalDateTime expiresAt = now.plusMinutes(VERIFICATION_CODE_EXPIRATION_MINUTES);
        SignupEmailVerificationCode savedCode = issueSignupEmailVerificationCode(
                request.getEmail(),
                codeHash,
                now,
                expiresAt
        );

        eventPublisher.publishEvent(new SignupEmailVerificationCodeSendEvent(
                savedCode.getEmail(),
                verificationCode
        ));

        return SignupEmailVerificationCodeSendResponse.builder()
                .sent(true)
                .verificationId(savedCode.getSignupEmailVerificationCodeId())
                .build();
    }

    private SignupEmailVerificationCode issueSignupEmailVerificationCode(
            String email,
            String codeHash,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        try {
            return signupEmailVerificationCodeIssuer.createOrReissue(
                    email,
                    codeHash,
                    issuedAt,
                    expiresAt
            );
        } catch (DataIntegrityViolationException e) {
            return signupEmailVerificationCodeIssuer.reissueExisting(
                    email,
                    codeHash,
                    issuedAt,
                    expiresAt
            );
        }
    }

    public SignupEmailVerificationCodeVerifyResponse verifySignupEmailVerificationCode(
            SignupEmailVerificationCodeVerifyRequest request
    ) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        SignupEmailVerificationCode savedCode = signupEmailVerificationCodeRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_EMAIL_VERIFICATION_CODE_NOT_FOUND));

        if (savedCode.isVerified()) {
            throw new BusinessException(ErrorCode.SIGNUP_EMAIL_ALREADY_VERIFIED);
        }

        LocalDateTime now = LocalDateTime.now();
        if (savedCode.isExpired(now)) {
            throw new BusinessException(ErrorCode.EXPIRED_SIGNUP_EMAIL_VERIFICATION_CODE);
        }

        if (!passwordEncoder.matches(request.getVerificationCode(), savedCode.getCodeHash())) {
            throw new BusinessException(ErrorCode.INVALID_SIGNUP_EMAIL_VERIFICATION_CODE);
        }

        savedCode.verify(now);

        return SignupEmailVerificationCodeVerifyResponse.builder()
                .verified(true)
                .build();
    }


    // 로그인 서비스
    public UserLoginResponse login(UserLoginRequest request) {
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        validateActiveUser(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        if(request.getFcmToken() != null && !request.getFcmToken().isBlank()){
            deviceService.registerToken(user, request.getFcmToken(), request.getDeviceIdentifier());
        }

        String accessToken = jwtUtil.createAccessToken(user);
        String refreshToken = jwtUtil.createRefreshToken(user);
        refreshTokenService.saveOrRotate(user, request.getDeviceIdentifier(), refreshToken);

        return UserLoginResponse.builder()
                .usersId(user.getUsersId())
                .name(user.getName())
                .loginId(user.getLoginId())
                .role(user.getRole())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // 로그아웃 서비스: 같은 기기에서 다른 계정이 로그인해도 이전 계정으로 알림이 가지 않도록
    // 로그아웃하는 기기의 FCM 토큰을 비활성화한다.
    public void logout(User user, String deviceIdentifier) {
        deviceService.clearToken(user, deviceIdentifier);
    }

    public OnboardingStatusResponse getOnboardingStatus(User principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }

        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Family family = user.getFamily();
        boolean hasFamily = family != null;
        Long seniorId = null;
        boolean seniorProfileCompleted = false;
        SeniorRelation relation = null;

        if (hasFamily) {
            Optional<UserSenior> userSenior =
                    userSeniorRepository.findFirstByUserAndSenior_FamilyOrderByUserSeniorIdAsc(user, family);
            Optional<Senior> familySenior = userSenior
                    .map(UserSenior::getSenior)
                    .or(() -> seniorRepository.findFirstByFamilyOrderBySeniorIdAsc(family));

            seniorId = familySenior
                    .map(Senior::getSeniorId)
                    .orElse(null);
            seniorProfileCompleted = familySenior.isPresent();
            relation = userSenior
                    .map(UserSenior::getRelation)
                    .orElse(null);
        }

        boolean onboardingCompleted =
                hasFamily
                        && user.getManagerType() != null
                        && user.getManagerType() != ManagerType.NONE
                        && seniorId != null
                        && seniorProfileCompleted
                        && relation != null;

        return OnboardingStatusResponse.builder()
                .hasFamily(hasFamily)
                .managerType(user.getManagerType())
                .seniorId(seniorId)
                .seniorProfileCompleted(seniorProfileCompleted)
                .relation(relation)
                .onboardingCompleted(onboardingCompleted)
                .build();
    }

    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        Long usersId = getRefreshTokenUsersId(refreshToken);
        String tokenHash = refreshTokenService.hashToken(refreshToken);

        RefreshToken savedRefreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!Objects.equals(savedRefreshToken.getUser().getUsersId(), usersId)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (savedRefreshToken.isRevoked()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (savedRefreshToken.isExpired(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        if (!Objects.equals(savedRefreshToken.getDeviceIdentifier(), request.getDeviceIdentifier())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_DEVICE_MISMATCH);
        }

        User user = savedRefreshToken.getUser();
        validateActiveUser(user);

        String newAccessToken = jwtUtil.createAccessToken(user);
        String newRefreshToken = jwtUtil.createRefreshToken(user);
        savedRefreshToken.rotate(refreshTokenService.hashToken(newRefreshToken), jwtUtil.getRefreshTokenExpiresAt());

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    public void withdraw(User principal, UserWithdrawalRequest request) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }

        User user = userRepository.findByIdForUpdate(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        if (!WITHDRAWAL_CONFIRMATION.equals(request.getConfirmation())) {
            throw new BusinessException(ErrorCode.INVALID_WITHDRAWAL_CONFIRMATION);
        }

        if (user.getManagerType() == ManagerType.PRIMARY) {
            throw new BusinessException(ErrorCode.PRIMARY_USER_CANNOT_WITHDRAW);
        }

        refreshTokenRepository.deleteAllByUser(user);
        deviceRepository.deleteAllByUser(user);
        socialAccountRepository.deleteAllByUser(user);
        userSeniorRepository.deleteAllByUser(user);

        anonymizeAndWithdraw(user);
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

    private void validateActiveUser(User user) {
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }
    }

    private void validateSignupEmailVerified(
            SignupEmailVerificationCode verificationCode,
            LocalDateTime now
    ) {
        if (!verificationCode.isVerified()) {
            throw new BusinessException(ErrorCode.SIGNUP_EMAIL_NOT_VERIFIED);
        }

        if (verificationCode.isExpired(now)) {
            throw new BusinessException(ErrorCode.EXPIRED_SIGNUP_EMAIL_VERIFICATION_CODE);
        }
    }

    private void anonymizeAndWithdraw(User user) {
        String suffix = user.getUsersId() + "_" + UUID.randomUUID().toString().replace("-", "");
        String anonymizedPassword = passwordEncoder.encode("withdrawn_" + suffix);

        user.withdraw(
                "withdrawn_" + suffix,
                "withdrawn_" + suffix + "@deleted.local",
                anonymizedPassword,
                LocalDateTime.now()
        );
    }

    private Long getRefreshTokenUsersId(String refreshToken) {
        try {
            if (!jwtUtil.isRefreshToken(refreshToken)) {
                throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
            }

            return jwtUtil.getUsersId(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

}
