package com.example.senioron.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.domain.admin.dto.request.AdminLoginRequest;
import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.entity.AdminStatus;
import com.example.senioron.domain.admin.repository.AdminRefreshTokenRepository;
import com.example.senioron.domain.admin.repository.AdminRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("local")
class AdminLoginSuspendConcurrencyTest {

    private static final String PASSWORD = "password1";

    @Autowired private AdminAuthService authService;
    @Autowired private AdminRepository admins;
    @Autowired private AdminRefreshTokenRepository refreshTokens;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    private Admin target;

    @BeforeEach
    void setUp() {
        target = admins.saveAndFlush(Admin.builder()
                .email("login-race-" + UUID.randomUUID() + "@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .name("로그인 경합")
                .adminRole(AdminRole.MODERATOR)
                .status(AdminStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void tearDown() {
        refreshTokens.findAll().stream()
                .filter(token -> token.getAdmin().getAdminId().equals(target.getAdminId()))
                .forEach(refreshTokens::delete);
        admins.deleteById(target.getAdminId());
    }

    @Test
    void loginWaitingOnSuspensionLockIsRejectedAndLeavesNoUsableRefreshToken() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CompletableFuture<Void> suspension = CompletableFuture.runAsync(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    Admin locking = admins.findByIdForUpdate(target.getAdminId()).orElseThrow();
                    locking.suspend();
                    admins.flush();
                    locked.countDown();
                    sleep(300);
                }));
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> authService.login(new AdminLoginRequest(target.getEmail(), PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ADMIN_NOT_ACTIVE));
        suspension.get(5, TimeUnit.SECONDS);

        assertThat(admins.findById(target.getAdminId()).orElseThrow().getStatus())
                .isEqualTo(AdminStatus.SUSPENDED);
        assertThat(refreshTokens.findAll())
                .noneMatch(token -> token.getAdmin().getAdminId().equals(target.getAdminId())
                        && !token.isRevoked());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
