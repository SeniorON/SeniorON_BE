package com.example.senioron.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;

import com.example.senioron.domain.inactivity.service.InactivitySettingService;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.user.dto.request.UserLoginRequest;
import com.example.senioron.domain.user.dto.request.PasswordChangeRequest;
import com.example.senioron.domain.user.dto.response.UserLoginResponse;
import com.example.senioron.domain.user.dto.response.PasswordChangeResponse;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UserSettingsServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CURRENT_PASSWORD = "Current123!";
    private static final String NEW_PASSWORD = "NewPassword123!";

    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserSettingsService userSettingsService;

    @BeforeEach
    void setUp() {
        userSettingsService = new UserSettingsService(userRepository, passwordEncoder);
    }

    @Test
    void changePasswordChangesEncodedPassword() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        PasswordChangeResponse response = userSettingsService.changePassword(
                user,
                createRequest(CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
        );

        assertThat(response.getChanged()).isTrue();
        assertThat(passwordEncoder.matches(CURRENT_PASSWORD, user.getPassword())).isFalse();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPassword())).isTrue();
    }

    @Test
    void changedPasswordRejectsOldPasswordAndAcceptsNewPasswordOnLogin() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userRepository.findByLoginId("testuser")).willReturn(Optional.of(user));

        UserService userService = new UserService(
                userRepository,
                passwordEncoder,
                new JwtUtil("12345678901234567890123456789012", 3600000L),
                org.mockito.Mockito.mock(NotificationService.class),
                org.mockito.Mockito.mock(InactivitySettingService.class)
        );

        UserLoginResponse beforeChange = userService.login(createLoginRequest(CURRENT_PASSWORD));
        assertThat(beforeChange.getAccessToken()).isNotBlank();

        userSettingsService.changePassword(
                user,
                createRequest(CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
        );

        assertThatThrownBy(() -> userService.login(createLoginRequest(CURRENT_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INVALID_PASSWORD);

        UserLoginResponse afterChange = userService.login(createLoginRequest(NEW_PASSWORD));
        assertThat(afterChange.getAccessToken()).isNotBlank();
    }

    @Test
    void changePasswordThrowsExceptionWhenCurrentPasswordMismatch() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userSettingsService.changePassword(
                user,
                createRequest("Wrong123!", NEW_PASSWORD, NEW_PASSWORD)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH);
    }

    @Test
    void changePasswordThrowsExceptionWhenNewPasswordConfirmationMismatch() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userSettingsService.changePassword(
                user,
                createRequest(CURRENT_PASSWORD, NEW_PASSWORD, "OtherPassword123!")
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.NEW_PASSWORD_CONFIRMATION_MISMATCH);
    }

    @Test
    void changePasswordThrowsExceptionWhenNewPasswordSameAsCurrentPassword() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userSettingsService.changePassword(
                user,
                createRequest(CURRENT_PASSWORD, CURRENT_PASSWORD, CURRENT_PASSWORD)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SAME_AS_CURRENT_PASSWORD);
    }

    private User createUser(String encodedPassword) {
        return User.builder()
                .usersId(USER_ID)
                .loginId("testuser")
                .email("test@example.com")
                .password(encodedPassword)
                .name("테스트")
                .build();
    }

    private PasswordChangeRequest createRequest(
            String currentPassword,
            String newPassword,
            String newPasswordCheck
    ) {
        PasswordChangeRequest request = new PasswordChangeRequest();
        ReflectionTestUtils.setField(request, "currentPassword", currentPassword);
        ReflectionTestUtils.setField(request, "newPassword", newPassword);
        ReflectionTestUtils.setField(request, "newPasswordCheck", newPasswordCheck);
        return request;
    }

    private UserLoginRequest createLoginRequest(String password) {
        UserLoginRequest request = new UserLoginRequest();
        ReflectionTestUtils.setField(request, "loginId", "testuser");
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }
}
