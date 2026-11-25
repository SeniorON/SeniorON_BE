package com.example.senioron.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.example.senioron.domain.user.dto.request.LoginIdFindRequest;
import com.example.senioron.domain.user.dto.response.LoginIdFindResponse;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.AccountRecoveryVerificationCodeRepository;
import com.example.senioron.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AccountRecoveryServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountRecoveryVerificationCodeRepository verificationCodeRepository =
            mock(AccountRecoveryVerificationCodeRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EmailVerificationRateLimitService emailVerificationRateLimitService =
            mock(EmailVerificationRateLimitService.class);

    private AccountRecoveryService accountRecoveryService;

    @BeforeEach
    void setUp() {
        accountRecoveryService = new AccountRecoveryService(
                userRepository,
                verificationCodeRepository,
                passwordEncoder,
                eventPublisher,
                emailVerificationRateLimitService
        );
    }

    @Test
    void findLoginIdReturnsCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 12, 10, 30);
        User user = User.builder()
                .usersId(1L)
                .loginId("testId")
                .email("test@example.com")
                .name("test")
                .role(Role.CHILD)
                .build();
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        given(userRepository.findByNameAndEmail("test", "test@example.com"))
                .willReturn(Optional.of(user));

        LoginIdFindRequest request = new LoginIdFindRequest();
        ReflectionTestUtils.setField(request, "name", "test");
        ReflectionTestUtils.setField(request, "email", "test@example.com");

        LoginIdFindResponse response = accountRecoveryService.findLoginId(request);

        assertThat(response.getLoginId()).isEqualTo("testId");
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    }
}
