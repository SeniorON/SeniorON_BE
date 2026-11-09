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
public class AdminPasswordResetTransactionService {
    private final AdminRepository adminRepository;
    private final AdminInvitationRepository invitationRepository;
    private final AdminInvitationIssuer invitationIssuer;
    private final AdminActivityLogService activityLogService;

    // 재설정 메일은 AFTER_COMMIT 리스너가 보내므로 이 트랜잭션이 롤백되면 발송되지 않는다.
    @Transactional
    public void issueForActiveAdmin(String email, String ipAddress) {
        Admin target = adminRepository.findByEmailForUpdate(email)
                .filter(Admin::isActive)
                .orElse(null);
        if (target == null) {
            return;
        }
        reissueResetLink(target, target);
        activityLogService.record(target, AdminActivityType.ADMIN_PASSWORD_RESET_REQUESTED,
                AdminActivityTargetType.ADMIN, target.getAdminId(), target.getName(),
                "관리자 비밀번호 재설정 메일을 요청했습니다.", ipAddress);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reissueResetLink(Admin target, Admin createdBy) {
        LocalDateTime now = LocalDateTime.now();
        invitationRepository.findAllByAdminAndType(target, AdminInvitationType.RESET)
                .stream().filter(invitation -> invitation.isUsable(now))
                .forEach(invitation -> invitation.invalidate(now));
        invitationIssuer.issue(target, createdBy, AdminInvitationType.RESET);
    }
}
