package com.example.senioron.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.example.senioron.domain.admin.entity.*;
import com.example.senioron.domain.admin.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

class AdminBootstrapBehaviorTest {
    private final AdminRepository admins = mock(AdminRepository.class);
    private final AdminInvitationRepository invitations = mock(AdminInvitationRepository.class);
    private final AdminInvitationIssuer issuer = mock(AdminInvitationIssuer.class);
    private final AdminBootstrapTransactionService service =
            new AdminBootstrapTransactionService(admins, invitations, issuer);

    @Test
    void activeBootstrapAdminDoesNotIssueInvitationOrEvent() {
        Admin active = admin(AdminStatus.ACTIVE);
        given(admins.findByIdForUpdate(1L)).willReturn(Optional.of(active));
        service.ensureInvitation(1L);
        verifyNoInteractions(invitations, issuer);
    }

    @Test
    void usableBootstrapInvitationIsNotReissued() {
        Admin invited = admin(AdminStatus.INVITED);
        given(admins.findByIdForUpdate(1L)).willReturn(Optional.of(invited));
        given(invitations.findAllByAdminAndType(invited, AdminInvitationType.INVITE))
                .willReturn(List.of(invitation(invited, LocalDateTime.now().plusHours(1))));
        service.ensureInvitation(1L);
        verify(issuer, never()).issue(any(), any());
    }

    @Test
    void expiredBootstrapInvitationCausesOneNewInvitation() {
        Admin invited = admin(AdminStatus.INVITED);
        given(admins.findByIdForUpdate(1L)).willReturn(Optional.of(invited));
        given(invitations.findAllByAdminAndType(invited, AdminInvitationType.INVITE))
                .willReturn(List.of(invitation(invited, LocalDateTime.now().minusHours(1))));
        service.ensureInvitation(1L);
        verify(issuer).issue(invited, null);
    }

    @Test
    void bootstrapRunnerSwallowsInvitationServiceFailure() {
        AdminInvitationService invitationService = mock(AdminInvitationService.class);
        doThrow(new RuntimeException("failure")).when(invitationService).ensureBootstrap("root@example.com");
        AdminBootstrapRunner runner = new AdminBootstrapRunner(invitationService);
        ReflectionTestUtils.setField(runner, "bootstrapEmail", "root@example.com");
        assertThatCode(() -> runner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    private Admin admin(AdminStatus status) {
        return Admin.builder().adminId(1L).email("root@example.com").name("root")
                .adminRole(AdminRole.SUPER).status(status).build();
    }

    private AdminInvitation invitation(Admin admin, LocalDateTime expiresAt) {
        return AdminInvitation.builder().admin(admin).type(AdminInvitationType.INVITE)
                .tokenHash("hash").expiresAt(expiresAt).build();
    }
}
