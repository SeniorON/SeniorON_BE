package com.example.senioron.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.inactivity.service.InactivitySettingService;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.UserSenior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.UserSeniorRepository;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.dto.request.SignupEmailVerificationCodeSendRequest;
import com.example.senioron.domain.user.dto.request.SignupEmailVerificationCodeVerifyRequest;
import com.example.senioron.domain.user.dto.request.TokenRefreshRequest;
import com.example.senioron.domain.user.dto.request.UserLoginRequest;
import com.example.senioron.domain.user.dto.request.UserSignUpRequest;
import com.example.senioron.domain.user.dto.request.UserWithdrawalRequest;
import com.example.senioron.domain.user.dto.response.OnboardingStatusResponse;
import com.example.senioron.domain.user.dto.response.SignupEmailVerificationCodeVerifyResponse;
import com.example.senioron.domain.user.dto.response.TokenRefreshResponse;
import com.example.senioron.domain.user.dto.response.UserLoginResponse;
import com.example.senioron.domain.user.dto.response.UserSignUpResponse;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.SignupEmailVerificationCode;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.event.SignupEmailVerificationCodeSendEvent;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.domain.user.repository.SignupEmailVerificationCodeRepository;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UserServiceTest {

    private static final String EMAIL = "test@example.com";
    private static final String VERIFICATION_CODE = "123456";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final SignupEmailVerificationCodeRepository signupEmailVerificationCodeRepository =
            mock(SignupEmailVerificationCodeRepository.class);
    private final SignupEmailVerificationCodeIssuer signupEmailVerificationCodeIssuer =
            mock(SignupEmailVerificationCodeIssuer.class);
    private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final SeniorRepository seniorRepository = mock(SeniorRepository.class);
    private final UserSeniorRepository userSeniorRepository = mock(UserSeniorRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final JwtUtil jwtUtil = new JwtUtil("12345678901234567890123456789012", 3600000L, 1209600000L);
    private final RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenRepository, jwtUtil);

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                refreshTokenRepository,
                signupEmailVerificationCodeRepository,
                signupEmailVerificationCodeIssuer,
                socialAccountRepository,
                refreshTokenService,
                deviceRepository,
                seniorRepository,
                userSeniorRepository,
                passwordEncoder,
                jwtUtil,
                mock(InactivitySettingService.class),
                mock(DeviceService.class),
                eventPublisher
        );
    }

    @Test
    void signUpSavesRequestedRole() {
        UserSignUpRequest request = createSignUpRequest(Role.CHILD);
        SignupEmailVerificationCode savedCode = createVerifiedSignupEmailVerificationCode();
        given(userRepository.existsByLoginId("testId")).willReturn(false);
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(signupEmailVerificationCodeRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedCode));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "usersId", 1L);
            return user;
        });

        UserSignUpResponse response = userService.signUp(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getRole()).isEqualTo(Role.CHILD);
        assertThat(response.getRole()).isEqualTo(Role.CHILD);
        verify(signupEmailVerificationCodeRepository).delete(savedCode);
    }

    @Test
    void signUpThrowsExceptionWhenSignupEmailVerificationMissing() {
        UserSignUpRequest request = createSignUpRequest(Role.CHILD);
        given(userRepository.existsByLoginId("testId")).willReturn(false);
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(signupEmailVerificationCodeRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.signUp(request))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SIGNUP_EMAIL_VERIFICATION_CODE_NOT_FOUND);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void signUpThrowsExceptionWhenSignupEmailIsNotVerified() {
        UserSignUpRequest request = createSignUpRequest(Role.CHILD);
        SignupEmailVerificationCode savedCode = createVerificationCode(
                passwordEncoder.encode(VERIFICATION_CODE),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(4)
        );
        given(userRepository.existsByLoginId("testId")).willReturn(false);
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(signupEmailVerificationCodeRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedCode));

        assertThatThrownBy(() -> userService.signUp(request))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SIGNUP_EMAIL_NOT_VERIFIED);

        verify(userRepository, never()).save(any(User.class));
        verify(signupEmailVerificationCodeRepository, never()).delete(any(SignupEmailVerificationCode.class));
    }

    @Test
    void signUpThrowsExceptionWhenSignupEmailVerificationExpired() {
        UserSignUpRequest request = createSignUpRequest(Role.CHILD);
        SignupEmailVerificationCode savedCode = createVerificationCode(
                passwordEncoder.encode(VERIFICATION_CODE),
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().minusMinutes(1)
        );
        savedCode.verify(LocalDateTime.now().minusMinutes(2));
        given(userRepository.existsByLoginId("testId")).willReturn(false);
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(signupEmailVerificationCodeRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedCode));

        assertThatThrownBy(() -> userService.signUp(request))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.EXPIRED_SIGNUP_EMAIL_VERIFICATION_CODE);

        verify(userRepository, never()).save(any(User.class));
        verify(signupEmailVerificationCodeRepository, never()).delete(any(SignupEmailVerificationCode.class));
    }

    @Test
    void loginReturnsUserRole() {
        User user = User.builder()
                .usersId(1L)
                .loginId("testId")
                .email(EMAIL)
                .password(passwordEncoder.encode("password123!"))
                .name("test")
                .role(Role.PARENT)
                .build();
        given(userRepository.findByLoginId("testId")).willReturn(Optional.of(user));
        given(refreshTokenRepository.findByUserAndDeviceIdentifier(user, null)).willReturn(Optional.empty());

        UserLoginResponse response = userService.login(createLoginRequest());

        assertThat(response.getRole()).isEqualTo(Role.PARENT);
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        verify(refreshTokenRepository).saveAndFlush(any(RefreshToken.class));
    }

    @Test
    void refreshTokenRotatesRefreshTokenAndReturnsNewTokens() {
        User user = User.builder()
                .usersId(1L)
                .loginId("testId")
                .email(EMAIL)
                .password(passwordEncoder.encode("password123!"))
                .name("test")
                .role(Role.PARENT)
                .build();
        given(userRepository.findByLoginId("testId")).willReturn(Optional.of(user));
        given(refreshTokenRepository.findByUserAndDeviceIdentifier(user, null)).willReturn(Optional.empty());

        UserLoginResponse loginResponse = userService.login(createLoginRequest());
        RefreshToken savedRefreshToken = captureSavedRefreshToken();
        String previousTokenHash = savedRefreshToken.getTokenHash();
        given(refreshTokenRepository.findByTokenHash(previousTokenHash)).willReturn(Optional.of(savedRefreshToken));

        TokenRefreshResponse response = userService.refreshToken(
                createTokenRefreshRequest(loginResponse.getRefreshToken())
        );

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotEqualTo(loginResponse.getRefreshToken());
        assertThat(savedRefreshToken.getTokenHash()).isNotEqualTo(previousTokenHash);
    }

    @Test
    void withdrawSucceedsWithValidConfirmation() {
        User user = createWithdrawalUser(ManagerType.SUB);
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));

        userService.withdraw(user, createWithdrawalRequest(UserService.WITHDRAWAL_CONFIRMATION));

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isNotNull();
        assertThat(user.getLoginId()).startsWith("withdrawn_1_");
        assertThat(user.getEmail()).startsWith("withdrawn_1_").endsWith("@deleted.local");
        assertThat(user.getName()).isEqualTo("탈퇴회원");
        assertThat(user.getPhoneNumber()).isNull();
        assertThat(user.getBirth()).isNull();
        assertThat(user.getProfileImageKey()).isNull();
        assertThat(user.getFamily()).isNull();
        assertThat(user.getManagerType()).isNull();
        assertThat(passwordEncoder.matches("password123!", user.getPassword())).isFalse();
        verify(refreshTokenRepository).deleteAllByUser(user);
        verify(deviceRepository).deleteAllByUser(user);
        verify(socialAccountRepository).deleteAllByUser(user);
        verify(userSeniorRepository).deleteAllByUser(user);
    }

    @Test
    void withdrawFailsWhenConfirmationMismatch() {
        User user = createWithdrawalUser(ManagerType.SUB);
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.withdraw(user, createWithdrawalRequest("탈퇴")))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INVALID_WITHDRAWAL_CONFIRMATION);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLoginId()).isEqualTo("withdraw-user");
        verify(refreshTokenRepository, never()).deleteAllByUser(any(User.class));
        verify(deviceRepository, never()).deleteAllByUser(any(User.class));
        verify(socialAccountRepository, never()).deleteAllByUser(any(User.class));
        verify(userSeniorRepository, never()).deleteAllByUser(any(User.class));
    }

    @Test
    void primaryUserCannotWithdrawAndDataIsUnchanged() {
        User user = createWithdrawalUser(ManagerType.PRIMARY);
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.withdraw(user, createWithdrawalRequest(UserService.WITHDRAWAL_CONFIRMATION)))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PRIMARY_USER_CANNOT_WITHDRAW);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getWithdrawnAt()).isNull();
        assertThat(user.getLoginId()).isEqualTo("withdraw-user");
        assertThat(user.getEmail()).isEqualTo("withdraw@example.com");
        assertThat(user.getName()).isEqualTo("탈퇴 전 사용자");
        assertThat(user.getManagerType()).isEqualTo(ManagerType.PRIMARY);
        verify(refreshTokenRepository, never()).deleteAllByUser(any(User.class));
        verify(deviceRepository, never()).deleteAllByUser(any(User.class));
        verify(socialAccountRepository, never()).deleteAllByUser(any(User.class));
        verify(userSeniorRepository, never()).deleteAllByUser(any(User.class));
    }

    @Test
    void refreshTokenFailsForWithdrawnUser() {
        User user = createWithdrawalUser(ManagerType.SUB);
        user.withdraw(
                "withdrawn_1_test",
                "withdrawn_1_test@deleted.local",
                passwordEncoder.encode("withdrawn"),
                LocalDateTime.now()
        );
        String refreshToken = jwtUtil.createRefreshToken(user);
        RefreshToken savedRefreshToken = RefreshToken.builder()
                .user(user)
                .deviceIdentifier(null)
                .tokenHash(refreshTokenService.hashToken(refreshToken))
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build();
        given(refreshTokenRepository.findByTokenHash(savedRefreshToken.getTokenHash()))
                .willReturn(Optional.of(savedRefreshToken));

        assertThatThrownBy(() -> userService.refreshToken(createTokenRefreshRequest(refreshToken)))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.WITHDRAWN_USER);
    }

    @Test
    void getOnboardingStatusReturnsEmptyStateWhenFamilyIsMissing() {
        User user = User.builder()
                .usersId(1L)
                .loginId("testId")
                .email(EMAIL)
                .password("encoded")
                .name("test")
                .role(Role.CHILD)
                .managerType(ManagerType.NONE)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        OnboardingStatusResponse response = userService.getOnboardingStatus(user);

        assertThat(response.isHasFamily()).isFalse();
        assertThat(response.getManagerType()).isEqualTo(ManagerType.NONE);
        assertThat(response.getSeniorId()).isNull();
        assertThat(response.isSeniorProfileCompleted()).isFalse();
        assertThat(response.getRelation()).isNull();
        assertThat(response.isOnboardingCompleted()).isFalse();
    }

    @Test
    void getOnboardingStatusReturnsSeniorWithoutCompletionWhenRelationIsMissing() {
        Family family = Family.builder()
                .familyId(1L)
                .familyCode("ABC123")
                .build();
        User user = createChild(1L, family, ManagerType.SUB);
        Senior senior = createSenior(123L, family, user);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userSeniorRepository.findFirstByUserAndSenior_FamilyOrderByUserSeniorIdAsc(user, family)).willReturn(Optional.empty());
        given(seniorRepository.findFirstByFamilyOrderBySeniorIdAsc(family)).willReturn(Optional.of(senior));

        OnboardingStatusResponse response = userService.getOnboardingStatus(user);

        assertThat(response.isHasFamily()).isTrue();
        assertThat(response.getManagerType()).isEqualTo(ManagerType.SUB);
        assertThat(response.getSeniorId()).isEqualTo(123L);
        assertThat(response.isSeniorProfileCompleted()).isTrue();
        assertThat(response.getRelation()).isNull();
        assertThat(response.isOnboardingCompleted()).isFalse();
    }

    @Test
    void getOnboardingStatusReturnsCompletedStateWhenRequiredValuesExist() {
        Family family = Family.builder()
                .familyId(1L)
                .familyCode("ABC123")
                .build();
        User user = createChild(1L, family, ManagerType.PRIMARY);
        Senior senior = createSenior(123L, family, user);
        UserSenior userSenior = UserSenior.builder()
                .user(user)
                .senior(senior)
                .relation(SeniorRelation.MOTHER)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userSeniorRepository.findFirstByUserAndSenior_FamilyOrderByUserSeniorIdAsc(user, family)).willReturn(Optional.of(userSenior));

        OnboardingStatusResponse response = userService.getOnboardingStatus(user);

        assertThat(response.isHasFamily()).isTrue();
        assertThat(response.getManagerType()).isEqualTo(ManagerType.PRIMARY);
        assertThat(response.getSeniorId()).isEqualTo(123L);
        assertThat(response.isSeniorProfileCompleted()).isTrue();
        assertThat(response.getRelation()).isEqualTo(SeniorRelation.MOTHER);
        assertThat(response.isOnboardingCompleted()).isTrue();
    }

    @Test
    void verifySignupEmailVerificationCodeSucceeds() {
        SignupEmailVerificationCode savedCode = createVerificationCode(
                passwordEncoder.encode(VERIFICATION_CODE),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(4)
        );
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(signupEmailVerificationCodeRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedCode));

        SignupEmailVerificationCodeVerifyResponse response = userService.verifySignupEmailVerificationCode(
                createVerifyRequest(EMAIL, VERIFICATION_CODE)
        );

        assertThat(response.getVerified()).isTrue();
        assertThat(savedCode.isVerified()).isTrue();
        assertThat(savedCode.getVerifiedAt()).isNotNull();
    }

    @Test
    void verifySignupEmailVerificationCodeThrowsExceptionWhenCodeMismatch() {
        SignupEmailVerificationCode savedCode = createVerificationCode(
                passwordEncoder.encode(VERIFICATION_CODE),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(4)
        );
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(signupEmailVerificationCodeRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedCode));

        assertThatThrownBy(() -> userService.verifySignupEmailVerificationCode(
                createVerifyRequest(EMAIL, "000000")
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INVALID_SIGNUP_EMAIL_VERIFICATION_CODE);
        assertThat(savedCode.isVerified()).isFalse();
    }

    @Test
    void verifySignupEmailVerificationCodeThrowsExceptionWhenExpired() {
        SignupEmailVerificationCode savedCode = createVerificationCode(
                passwordEncoder.encode(VERIFICATION_CODE),
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().minusMinutes(1)
        );
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(signupEmailVerificationCodeRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedCode));

        assertThatThrownBy(() -> userService.verifySignupEmailVerificationCode(
                createVerifyRequest(EMAIL, VERIFICATION_CODE)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.EXPIRED_SIGNUP_EMAIL_VERIFICATION_CODE);
        assertThat(savedCode.isVerified()).isFalse();
    }

    @Test
    void verifySignupEmailVerificationCodeThrowsExceptionWhenRequestHistoryMissing() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(signupEmailVerificationCodeRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.verifySignupEmailVerificationCode(
                createVerifyRequest(EMAIL, VERIFICATION_CODE)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SIGNUP_EMAIL_VERIFICATION_CODE_NOT_FOUND);
    }

    @Test
    void verifySignupEmailVerificationCodeThrowsExceptionWhenEmailAlreadyExists() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> userService.verifySignupEmailVerificationCode(
                createVerifyRequest(EMAIL, VERIFICATION_CODE)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void verifySignupEmailVerificationCodeThrowsExceptionWhenAlreadyVerified() {
        SignupEmailVerificationCode savedCode = createVerificationCode(
                passwordEncoder.encode(VERIFICATION_CODE),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(4)
        );
        savedCode.verify(LocalDateTime.now());
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(signupEmailVerificationCodeRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedCode));

        assertThatThrownBy(() -> userService.verifySignupEmailVerificationCode(
                createVerifyRequest(EMAIL, VERIFICATION_CODE)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SIGNUP_EMAIL_ALREADY_VERIFIED);
    }

    @Test
    void sendSignupEmailVerificationCodeResetsVerifiedStateWhenReissued() {
        SignupEmailVerificationCode savedCode = createVerificationCode(
                passwordEncoder.encode(VERIFICATION_CODE),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(4)
        );
        savedCode.verify(LocalDateTime.now());
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(signupEmailVerificationCodeIssuer.createOrReissue(
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).willAnswer(invocation -> {
            savedCode.reissue(
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    invocation.getArgument(3)
            );
            return savedCode;
        });

        userService.sendSignupEmailVerificationCode(createSendRequest(EMAIL));

        assertThat(savedCode.isVerified()).isFalse();
        assertThat(savedCode.getVerifiedAt()).isNull();
        verify(eventPublisher).publishEvent(any(SignupEmailVerificationCodeSendEvent.class));
    }

    @Test
    void sendSignupEmailVerificationCodeRetriesWhenInitialInsertRaceOccurs() {
        SignupEmailVerificationCode savedCode = createVerificationCode(
                passwordEncoder.encode(VERIFICATION_CODE),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(4)
        );
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(signupEmailVerificationCodeIssuer.createOrReissue(
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).willThrow(new DataIntegrityViolationException("duplicate email"));
        given(signupEmailVerificationCodeIssuer.reissueExisting(
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).willReturn(savedCode);

        userService.sendSignupEmailVerificationCode(createSendRequest(EMAIL));

        verify(signupEmailVerificationCodeIssuer).reissueExisting(
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        );
        verify(eventPublisher).publishEvent(any(SignupEmailVerificationCodeSendEvent.class));
    }

    private SignupEmailVerificationCode createVerificationCode(
            String codeHash,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        return SignupEmailVerificationCode.builder()
                .email(EMAIL)
                .codeHash(codeHash)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }

    private SignupEmailVerificationCode createVerifiedSignupEmailVerificationCode() {
        SignupEmailVerificationCode savedCode = createVerificationCode(
                passwordEncoder.encode(VERIFICATION_CODE),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(4)
        );
        savedCode.verify(LocalDateTime.now().minusSeconds(30));
        return savedCode;
    }

    private SignupEmailVerificationCodeVerifyRequest createVerifyRequest(String email, String verificationCode) {
        SignupEmailVerificationCodeVerifyRequest request = new SignupEmailVerificationCodeVerifyRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "verificationCode", verificationCode);
        return request;
    }

    private SignupEmailVerificationCodeSendRequest createSendRequest(String email) {
        SignupEmailVerificationCodeSendRequest request = new SignupEmailVerificationCodeSendRequest();
        ReflectionTestUtils.setField(request, "email", email);
        return request;
    }

    private UserSignUpRequest createSignUpRequest(Role role) {
        UserSignUpRequest request = new UserSignUpRequest();
        ReflectionTestUtils.setField(request, "loginId", "testId");
        ReflectionTestUtils.setField(request, "email", EMAIL);
        ReflectionTestUtils.setField(request, "password", "password123!");
        ReflectionTestUtils.setField(request, "passwordCheck", "password123!");
        ReflectionTestUtils.setField(request, "name", "test");
        ReflectionTestUtils.setField(request, "birth", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(request, "role", role);
        ReflectionTestUtils.setField(request, "agreeServiceTerms", true);
        ReflectionTestUtils.setField(request, "agreePrivacyPolicy", true);
        ReflectionTestUtils.setField(request, "agreeAgeOver14", true);
        ReflectionTestUtils.setField(request, "agreeMarketing", false);
        return request;
    }

    private UserLoginRequest createLoginRequest() {
        UserLoginRequest request = new UserLoginRequest();
        ReflectionTestUtils.setField(request, "loginId", "testId");
        ReflectionTestUtils.setField(request, "password", "password123!");
        return request;
    }

    private TokenRefreshRequest createTokenRefreshRequest(String refreshToken) {
        TokenRefreshRequest request = new TokenRefreshRequest();
        ReflectionTestUtils.setField(request, "refreshToken", refreshToken);
        return request;
    }

    private UserWithdrawalRequest createWithdrawalRequest(String confirmation) {
        UserWithdrawalRequest request = new UserWithdrawalRequest();
        ReflectionTestUtils.setField(request, "confirmation", confirmation);
        return request;
    }

    private User createWithdrawalUser(ManagerType managerType) {
        return User.builder()
                .usersId(1L)
                .loginId("withdraw-user")
                .email("withdraw@example.com")
                .password(passwordEncoder.encode("password123!"))
                .name("탈퇴 전 사용자")
                .birth(LocalDate.of(1990, 1, 1))
                .phoneNumber("010-1234-5678")
                .role(Role.CHILD)
                .managerType(managerType)
                .status(UserStatus.ACTIVE)
                .profileImageKey("profile-images/1/original.webp")
                .build();
    }

    private User createChild(Long usersId, Family family, ManagerType managerType) {
        return User.builder()
                .usersId(usersId)
                .family(family)
                .loginId("child" + usersId)
                .email("child" + usersId + "@example.com")
                .password("encoded")
                .name("자녀")
                .role(Role.CHILD)
                .managerType(managerType)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Senior createSenior(Long seniorId, Family family, User registeredBy) {
        return Senior.builder()
                .seniorId(seniorId)
                .name("시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(family)
                .registeredBy(registeredBy)
                .build();
    }

    private RefreshToken captureSavedRefreshToken() {
        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).saveAndFlush(refreshTokenCaptor.capture());
        return refreshTokenCaptor.getValue();
    }
}
