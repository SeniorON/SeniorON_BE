package com.example.senioron.domain.admin.service;

import com.example.senioron.domain.admin.entity.*;
import com.example.senioron.domain.admin.repository.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminBootstrapTransactionService {
    private final AdminRepository adminRepository;
    private final AdminInvitationRepository invitationRepository;
    private final AdminInvitationIssuer issuer;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Admin createAdmin(String email) {
        return adminRepository.saveAndFlush(Admin.builder()
                .email(email)
                .name("최고 관리자")
                .adminRole(AdminRole.SUPER)
                .status(AdminStatus.INVITED)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureInvitation(Long adminId) {
        Admin admin = adminRepository.findByIdForUpdate(adminId).orElseThrow();
        if (admin.getStatus() != AdminStatus.INVITED) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean usable = invitationRepository.findAllByAdminAndType(admin, AdminInvitationType.INVITE)
                .stream().anyMatch(invitation -> invitation.isUsable(now));
        if (usable) {
            return;
        }
        issuer.issue(admin, null);
    }
}
