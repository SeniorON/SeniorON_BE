package com.example.senioron.domain.admin.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminTest {

    @Test
    void suspendChangesActiveAndInvitedButNotSuspended() {
        for (AdminStatus status : new AdminStatus[]{AdminStatus.ACTIVE, AdminStatus.INVITED}) {
            Admin admin = admin(status, "encoded");
            assertThat(admin.suspend()).isTrue();
            assertThat(admin.getStatus()).isEqualTo(AdminStatus.SUSPENDED);
        }
        assertThat(admin(AdminStatus.SUSPENDED, "encoded").suspend()).isFalse();
    }

    @Test
    void reactivateRestoresStatusByPasswordPresence() {
        Admin withPassword = admin(AdminStatus.SUSPENDED, "encoded");
        Admin withoutPassword = admin(AdminStatus.SUSPENDED, null);

        assertThat(withPassword.reactivate()).isTrue();
        assertThat(withPassword.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(withoutPassword.reactivate()).isTrue();
        assertThat(withoutPassword.getStatus()).isEqualTo(AdminStatus.INVITED);
    }

    @Test
    void reactivateKeepsActiveAndInvited() {
        Admin active = admin(AdminStatus.ACTIVE, "encoded");
        Admin invited = admin(AdminStatus.INVITED, null);

        assertThat(active.reactivate()).isFalse();
        assertThat(active.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(invited.reactivate()).isFalse();
        assertThat(invited.getStatus()).isEqualTo(AdminStatus.INVITED);
    }

    @Test
    void changeRoleReportsWhetherRoleChanged() {
        Admin admin = admin(AdminStatus.ACTIVE, "encoded");

        assertThat(admin.changeRole(AdminRole.MODERATOR)).isFalse();
        assertThat(admin.changeRole(AdminRole.SUPER)).isTrue();
        assertThat(admin.getAdminRole()).isEqualTo(AdminRole.SUPER);
    }

    private Admin admin(AdminStatus status, String password) {
        return Admin.builder()
                .email("entity@example.com")
                .password(password)
                .name("관리자")
                .adminRole(AdminRole.MODERATOR)
                .status(status)
                .build();
    }
}
