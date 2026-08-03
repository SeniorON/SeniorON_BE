package com.example.senioron.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.inactivity.service.InactivitySettingService;
import com.example.senioron.domain.user.dto.request.SignupEmailVerificationCodeSendRequest;
import com.example.senioron.domain.user.dto.request.SignupEmailVerificationCodeVerifyRequest;
import com.example.senioron.domain.user.dto.request.TokenRefreshRequest;
import com.example.senioron.domain.user.dto.request.UserLoginRequest;
import com.example.senioron.domain.user.dto.request.UserSignUpRequest;
import com.example.senioron.domain.user.dto.response.SignupEmailVerificationCodeVerifyResponse;
import com.example.senioron.domain.user.dto.response.TokenRefreshResponse;
import com.example.senioron.domain.user.dto.response.UserLoginResponse;
import com.example.senioron.domain.user.dto.response.UserSignUpResponse;
import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.SignupEmailVerificationCode;
import com.example.senioron.domain.user.entity.User;
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
                refreshTokenService,
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
        given(userRepository.existsByLoginId("testId")).willReturn(false);
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
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

    private RefreshToken captureSavedRefreshToken() {
        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).saveAndFlush(refreshTokenCaptor.capture());
        return refreshTokenCaptor.getValue();
    }
}
