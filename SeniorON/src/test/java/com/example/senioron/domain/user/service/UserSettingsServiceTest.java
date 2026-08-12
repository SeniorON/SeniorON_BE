package com.example.senioron.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.inactivity.service.InactivitySettingService;
import com.example.senioron.domain.user.dto.request.UserLoginRequest;
import com.example.senioron.domain.user.dto.request.PasswordChangeRequest;
import com.example.senioron.domain.user.dto.response.UserLoginResponse;
import com.example.senioron.domain.user.dto.response.PasswordChangeResponse;
import com.example.senioron.domain.user.dto.response.ProfileImageResponse;
import com.example.senioron.domain.user.dto.response.ProfileImageUpdateResponse;
import com.example.senioron.domain.user.dto.response.UserAccountResponse;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import com.example.senioron.global.storage.S3Service;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class UserSettingsServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CURRENT_PASSWORD = "Current123!";
    private static final String NEW_PASSWORD = "NewPassword123!";

    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final S3Service s3Service = org.mockito.Mockito.mock(S3Service.class);

    private UserSettingsService userSettingsService;

    @BeforeEach
    void setUp() {
        userSettingsService = new UserSettingsService(userRepository, passwordEncoder, s3Service);
    }

    @Test
    void getAccountReturnsCurrentUserNameRoleAndEmail() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));

        UserAccountResponse response = userSettingsService.getAccount(user);

        assertThat(response.getName()).isEqualTo("테스트");
        assertThat(response.getRole()).isEqualTo(Role.CHILD);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
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
        com.example.senioron.domain.user.repository.RefreshTokenRepository refreshTokenRepository =
                org.mockito.Mockito.mock(com.example.senioron.domain.user.repository.RefreshTokenRepository.class);
        given(refreshTokenRepository.findByUserAndDeviceIdentifier(user, null)).willReturn(Optional.empty());
        JwtUtil jwtUtil = new JwtUtil("12345678901234567890123456789012", 3600000L, 1209600000L);

        UserService userService = new UserService(
                userRepository,
                refreshTokenRepository,
                org.mockito.Mockito.mock(com.example.senioron.domain.user.repository.SignupEmailVerificationCodeRepository.class),
                org.mockito.Mockito.mock(SignupEmailVerificationCodeIssuer.class),
                org.mockito.Mockito.mock(com.example.senioron.domain.socialaccount.repository.SocialAccountRepository.class),
                new RefreshTokenService(refreshTokenRepository, jwtUtil),
                org.mockito.Mockito.mock(com.example.senioron.domain.device.repository.DeviceRepository.class),
                org.mockito.Mockito.mock(com.example.senioron.domain.senior.repository.SeniorRepository.class),
                org.mockito.Mockito.mock(com.example.senioron.domain.senior.repository.UserSeniorRepository.class),
                passwordEncoder,
                jwtUtil,
                org.mockito.Mockito.mock(InactivitySettingService.class),
                org.mockito.Mockito.mock(com.example.senioron.domain.device.service.DeviceService.class),
                org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class)
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

    @Test
    void updateProfileImageUploadsNewImageAndDeletesPreviousImage() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));
        user.updateProfileImageKey("profile-images/1/old.webp");
        MockMultipartFile image = createImage("profile.webp", "image/webp", 1024);
        String newImageKey = "profile-images/1/new.webp";
        String newImageUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + newImageKey;

        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(s3Service.upload(image, "profile-images/" + USER_ID)).willReturn(newImageKey);
        given(s3Service.getFileUrl(newImageKey)).willReturn(newImageUrl);

        ProfileImageUpdateResponse response = userSettingsService.updateProfileImage(user, image);

        assertThat(user.getProfileImageKey()).isEqualTo(newImageKey);
        assertThat(response.getProfileImageUrl()).isEqualTo(newImageUrl);
        InOrder inOrder = inOrder(userRepository, s3Service);
        inOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        inOrder.verify(s3Service).upload(image, "profile-images/" + USER_ID);
        verify(userRepository).flush();
        verify(s3Service).delete("profile-images/1/old.webp");
    }

    @Test
    void updateProfileImageThrowsExceptionWhenImageMissing() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));

        assertThatThrownBy(() -> userSettingsService.updateProfileImage(user, null))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PROFILE_IMAGE_REQUIRED);

        verify(s3Service, never()).upload(org.mockito.Mockito.any(), org.mockito.Mockito.anyString());
    }

    @Test
    void updateProfileImageThrowsExceptionWhenUnsupportedContentType() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));
        MockMultipartFile image = createImage("profile.gif", "image/gif", 1024);

        assertThatThrownBy(() -> userSettingsService.updateProfileImage(user, image))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE);

        verify(s3Service, never()).upload(org.mockito.Mockito.any(), org.mockito.Mockito.anyString());
    }

    @Test
    void updateProfileImageThrowsExceptionWhenFileSizeExceeded() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));
        MockMultipartFile image = createImage("profile.jpg", "image/jpeg", 10 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> userSettingsService.updateProfileImage(user, image))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PROFILE_IMAGE_SIZE_EXCEEDED);

        verify(s3Service, never()).upload(org.mockito.Mockito.any(), org.mockito.Mockito.anyString());
    }

    @Test
    void getProfileImageReturnsProfileImageUrl() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));
        String imageKey = "profile-images/1/profile.webp";
        String imageUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + imageKey;
        user.updateProfileImageKey(imageKey);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(s3Service.getFileUrl(imageKey)).willReturn(imageUrl);

        ProfileImageResponse response = userSettingsService.getProfileImage(user);

        assertThat(response.getProfileImageUrl()).isEqualTo(imageUrl);
        assertThat(response.getIsDefaultProfileImage()).isFalse();
    }

    @Test
    void getProfileImageReturnsNullAndDefaultFlagWhenProfileImageMissing() {
        User user = createUser(passwordEncoder.encode(CURRENT_PASSWORD));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        ProfileImageResponse response = userSettingsService.getProfileImage(user);

        assertThat(response.getProfileImageUrl()).isNull();
        assertThat(response.getIsDefaultProfileImage()).isTrue();
        verify(s3Service, never()).getFileUrl(org.mockito.Mockito.any());
    }

    private User createUser(String encodedPassword) {
        return User.builder()
                .usersId(USER_ID)
                .loginId("testuser")
                .email("test@example.com")
                .password(encodedPassword)
                .name("테스트")
                .role(Role.CHILD)
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

    private MockMultipartFile createImage(String originalFilename, String contentType, int size) {
        return new MockMultipartFile(
                "image",
                originalFilename,
                contentType,
                new byte[size]
        );
    }
}
