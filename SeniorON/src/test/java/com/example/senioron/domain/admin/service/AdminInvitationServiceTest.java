package com.example.senioron.domain.admin.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.example.senioron.domain.admin.dto.request.*;
import com.example.senioron.domain.admin.entity.*;
import com.example.senioron.domain.admin.repository.*;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminInvitationServiceTest {
    private final AdminRepository admins = mock(AdminRepository.class);
    private final AdminInvitationRepository invitations = mock(AdminInvitationRepository.class);
    private final AdminActivityLogService logs = mock(AdminActivityLogService.class);
    private final AdminBootstrapTransactionService bootstrap = mock(AdminBootstrapTransactionService.class);
    private final AdminInvitationIssuer issuer = mock(AdminInvitationIssuer.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private AdminInvitationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AdminInvitationService(
                admins, invitations, logs, encoder, bootstrap, issuer
        );
    }

    @Test
    void invalidInvitationStatesReturnSameError() {
        Admin active = admin(AdminStatus.INVITED);
        Admin suspended = admin(AdminStatus.SUSPENDED);
        for (AdminInvitation invitation : new AdminInvitation[]{
                invitation(active, LocalDateTime.now().minusMinutes(1), null, null),
                invitation(active, LocalDateTime.now().plusHours(1), LocalDateTime.now(), null),
                invitation(active, LocalDateTime.now().plusHours(1), null, LocalDateTime.now()),
                invitation(suspended, LocalDateTime.now().plusHours(1), null, null)
        }) {
            given(invitations.findByTokenHash(anyString())).willReturn(Optional.of(invitation));
            assertThatThrownBy(() -> service.verify(new AdminInvitationTokenRequest("token")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_INVITATION_TOKEN));
        }
    }

    @Test
    void passwordMustContainLettersAndNumbersAndHaveValidLength() {
        given(invitations.findByTokenHashForUpdate(anyString())).willReturn(Optional.of(
                invitation(admin(AdminStatus.INVITED), LocalDateTime.now().plusHours(1), null, null)
        ));
        for (String password : new String[]{"short1", "onlyletters", "12345678", "a1" + "x".repeat(63)}) {
            assertThatThrownBy(() -> service.accept(new AdminInvitationAcceptRequest("token", password)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_ADMIN_PASSWORD));
        }
    }

    @Test
    void acceptActivatesAdminAndUsesInvitation() {
        Admin admin = admin(AdminStatus.INVITED);
        AdminInvitation invitation = invitation(admin, LocalDateTime.now().plusHours(1), null, null);
        given(invitations.findByTokenHashForUpdate(anyString())).willReturn(Optional.of(invitation));
        service.accept(new AdminInvitationAcceptRequest("token", "password1"));
        assertThat(admin.isActive()).isTrue();
        assertThat(encoder.matches("password1", admin.getPassword())).isTrue();
        assertThat(invitation.getUsedAt()).isNotNull();
        verify(invitations).findByTokenHashForUpdate(anyString());
    }

    @Test
    void invitedEmailIgnoringCaseUpdatesProfileInvalidatesOldLinkAndReturnsResentTrue() {
        Admin target = admin(AdminStatus.INVITED);
        Admin actor = admin(AdminStatus.ACTIVE);
        AdminInvitation old = invitation(target, LocalDateTime.now().plusHours(1), null, null);
        given(admins.findByEmail("admin@example.com")).willReturn(Optional.of(target));
        given(admins.findByIdForUpdate(1L)).willReturn(Optional.of(target));
        given(invitations.findAllByAdminAndType(target, AdminInvitationType.INVITE))
                .willReturn(List.of(old));

        var response = service.invite(actor,
                new AdminInviteRequest("변경된 이름", "ADMIN@EXAMPLE.COM", AdminRole.SUPER),
                "127.0.0.1");

        assertThat(response.adminId()).isEqualTo(1L);
        assertThat(response.resent()).isTrue();
        assertThat(old.getInvalidatedAt()).isNotNull();
        assertThat(target.getName()).isEqualTo("변경된 이름");
        assertThat(target.getAdminRole()).isEqualTo(AdminRole.SUPER);
        verify(issuer).issue(target, actor);
        verify(logs).record(actor, AdminActivityType.ADMIN_INVITATION_RESENT,
                AdminActivityTargetType.ADMIN, 1L, "변경된 이름",
                "관리자 초대 링크를 재발송했습니다.", "127.0.0.1");
    }

    @Test
    void activeAndSuspendedEmailReturnDuplicateEmailConflict() {
        for (AdminStatus status : List.of(AdminStatus.ACTIVE, AdminStatus.SUSPENDED)) {
            reset(admins);
            Admin existing = admin(status);
            given(admins.findByEmail("admin@example.com")).willReturn(Optional.of(existing));
            given(admins.findByIdForUpdate(1L)).willReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.invite(admin(AdminStatus.ACTIVE),
                    new AdminInviteRequest("new", "ADMIN@EXAMPLE.COM", AdminRole.MODERATOR),
                    "127.0.0.1"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DUPLICATE_ADMIN_EMAIL));
        }
    }

    @Test
    void acceptingSameInvitationTwiceReturnsInvalidToken() {
        AdminInvitation invitation = invitation(admin(AdminStatus.INVITED), LocalDateTime.now().plusHours(1), null, null);
        given(invitations.findByTokenHashForUpdate(anyString())).willReturn(Optional.of(invitation));
        service.accept(new AdminInvitationAcceptRequest("token", "password1"));
        assertThatThrownBy(() -> service.accept(new AdminInvitationAcceptRequest("token", "password1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_INVITATION_TOKEN));
    }

    @Test
    void passwordBoundaryRejects7And65AndMissingCharacterClasses() {
        given(invitations.findByTokenHashForUpdate(anyString())).willReturn(Optional.of(
                invitation(admin(AdminStatus.INVITED), LocalDateTime.now().plusHours(1), null, null)
        ));
        for (String password : new String[]{"abc1234", "a1" + "x".repeat(63), "abcdefgh", "12345678"}) {
            assertThatThrownBy(() -> service.accept(new AdminInvitationAcceptRequest("token", password)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void passwordBoundaryAcceptsExactly8And64Characters() {
        for (String password : new String[]{"abcdefg1", "a1" + "x".repeat(62)}) {
            AdminInvitation invitation = invitation(admin(AdminStatus.INVITED), LocalDateTime.now().plusHours(1), null, null);
            given(invitations.findByTokenHashForUpdate(anyString())).willReturn(Optional.of(invitation));
            assertThatCode(() -> service.accept(new AdminInvitationAcceptRequest("token", password)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void invalidTokenTakesPrecedenceOverInvalidPassword() {
        given(invitations.findByTokenHashForUpdate(anyString())).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept(
                new AdminInvitationAcceptRequest("invalid-token", "short1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_INVITATION_TOKEN));
    }

    private Admin admin(AdminStatus status) {
        return Admin.builder().adminId(1L).email("admin@example.com").name("관리자")
                .adminRole(AdminRole.SUPER).status(status).build();
    }

    private AdminInvitation invitation(Admin admin, LocalDateTime expiresAt,
                                       LocalDateTime usedAt, LocalDateTime invalidatedAt) {
        return AdminInvitation.builder().admin(admin).type(AdminInvitationType.INVITE)
                .tokenHash("hash").expiresAt(expiresAt).usedAt(usedAt)
                .invalidatedAt(invalidatedAt).build();
    }
}
