package com.example.senioron.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.UserSenior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.UserSeniorRepository;
import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class UserWithdrawalRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private UserSeniorRepository userSeniorRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private SeniorRepository seniorRepository;

    @Test
    void withdrawnUserKeepsRowButReleasesUniqueIdentifiersAndRelationships() {
        Family family = familyRepository.saveAndFlush(Family.builder()
                .familyCode("ABCD-1234")
                .build());
        User user = userRepository.saveAndFlush(createUser("old-login", "old@example.com", family));
        Senior senior = seniorRepository.saveAndFlush(Senior.builder()
                .name("시니어")
                .birth(LocalDate.of(1940, 1, 1))
                .phoneNumber("010-0000-0000")
                .family(family)
                .registeredBy(user)
                .build());
        refreshTokenRepository.saveAndFlush(RefreshToken.builder()
                .user(user)
                .tokenHash("old-refresh-token-hash")
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build());
        deviceRepository.saveAndFlush(Device.builder()
                .user(user)
                .deviceIdentifier("device-1")
                .deviceToken("fcm-token")
                .connectionStatus(DeviceStatus.ONLINE)
                .build());
        socialAccountRepository.saveAndFlush(SocialAccount.builder()
                .user(user)
                .provider(LoginProvider.GOOGLE)
                .providerId("google-provider-id")
                .build());
        userSeniorRepository.saveAndFlush(UserSenior.builder()
                .user(user)
                .senior(senior)
                .relation(SeniorRelation.MOTHER)
                .build());

        refreshTokenRepository.deleteAllByUser(user);
        deviceRepository.deleteAllByUser(user);
        socialAccountRepository.deleteAllByUser(user);
        userSeniorRepository.deleteAllByUser(user);
        user.withdraw(
                "withdrawn_" + user.getUsersId() + "_test",
                "withdrawn_" + user.getUsersId() + "_test@deleted.local",
                "unusable-password-hash",
                LocalDateTime.now()
        );
        userRepository.flush();

        assertThat(userRepository.findById(user.getUsersId())).hasValueSatisfying(withdrawn -> {
            assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
            assertThat(withdrawn.getWithdrawnAt()).isNotNull();
        });
        assertThat(refreshTokenRepository.count()).isZero();
        assertThat(deviceRepository.count()).isZero();
        assertThat(socialAccountRepository.count()).isZero();
        assertThat(userSeniorRepository.count()).isZero();
        assertThat(familyRepository.findById(family.getFamilyId())).isPresent();
        assertThat(seniorRepository.findById(senior.getSeniorId())).isPresent();

        User rejoinedUser = userRepository.saveAndFlush(createUser("old-login", "old@example.com", family));
        socialAccountRepository.saveAndFlush(SocialAccount.builder()
                .user(rejoinedUser)
                .provider(LoginProvider.GOOGLE)
                .providerId("google-provider-id")
                .build());

        assertThat(userRepository.count()).isEqualTo(2L);
        assertThat(socialAccountRepository.count()).isEqualTo(1L);
    }

    @Test
    void socialUserCanBeSavedWithoutLoginIdAndPassword() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("new-social@example.com")
                .name("소셜 사용자")
                .birth(LocalDate.of(1990, 1, 1))
                .role(Role.CHILD)
                .status(UserStatus.ACTIVE)
                .build());

        SocialAccount socialAccount = socialAccountRepository.saveAndFlush(SocialAccount.builder()
                .user(user)
                .provider(LoginProvider.GOOGLE)
                .providerId("new-google-provider-id")
                .build());

        assertThat(user.getLoginId()).isNull();
        assertThat(user.getPassword()).isNull();
        assertThat(socialAccount.getUser().getUsersId()).isEqualTo(user.getUsersId());
    }

    private User createUser(String loginId, String email, Family family) {
        return User.builder()
                .loginId(loginId)
                .email(email)
                .password("encoded-password")
                .name("사용자")
                .birth(LocalDate.of(1990, 1, 1))
                .phoneNumber("010-1234-5678")
                .role(Role.CHILD)
                .managerType(ManagerType.SUB)
                .family(family)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
