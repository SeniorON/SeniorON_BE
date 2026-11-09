package com.example.senioron.domain.admin.service;

import com.example.senioron.domain.admin.dto.request.*;
import com.example.senioron.domain.admin.dto.response.*;
import com.example.senioron.domain.admin.entity.*;
import com.example.senioron.domain.admin.repository.*;
import com.example.senioron.domain.user.service.EmailVerificationRateLimitService;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.TokenHashUtil;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminInvitationService {

    private final AdminRepository adminRepository;
    private final AdminInvitationRepository invitationRepository;
    private final AdminActivityLogService activityLogService;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapTransactionService bootstrapTransactionService;
    private final AdminInvitationIssuer invitationIssuer;
    private final AdminRefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationRateLimitService rateLimitService;

    @Transactional
    public AdminInviteResponse invite(Admin actor, AdminInviteRequest request, String ipAddress) {
        String email = normalize(request.email());
        Admin existing = adminRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            return reinvite(actor, existing.getAdminId(), request, ipAddress);
        }

        Admin invited = Admin.builder()
                .email(email)
                .name(request.name())
                .adminRole(request.adminRole())
                .status(AdminStatus.INVITED)
                .build();
        try {
            adminRepository.saveAndFlush(invited);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_ADMIN_EMAIL);
        }

        invitationIssuer.issue(invited, actor);
        activityLogService.record(actor, AdminActivityType.ADMIN_INVITED,
                AdminActivityTargetType.ADMIN, invited.getAdminId(), invited.getName(),
                "관리자 계정을 초대했습니다.", ipAddress);
        return new AdminInviteResponse(invited.getAdminId(), false);
    }

    private AdminInviteResponse reinvite(
            Admin actor,
            Long adminId,
            AdminInviteRequest request,
            String ipAddress
    ) {
        Admin invited = adminRepository.findByIdForUpdate(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DUPLICATE_ADMIN_EMAIL));
        if (invited.getStatus() != AdminStatus.INVITED) {
            throw new BusinessException(ErrorCode.DUPLICATE_ADMIN_EMAIL);
        }
        LocalDateTime now = LocalDateTime.now();
        invitationRepository.findAllByAdminAndType(invited, AdminInvitationType.INVITE)
                .stream().filter(invitation -> invitation.isUsable(now))
                .forEach(invitation -> invitation.invalidate(now));
        invited.updateInvitedProfile(request.name(), request.adminRole());
        invitationIssuer.issue(invited, actor);
        activityLogService.record(actor, AdminActivityType.ADMIN_INVITATION_RESENT,
                AdminActivityTargetType.ADMIN, invited.getAdminId(), invited.getName(),
                "관리자 초대 링크를 재발송했습니다.", ipAddress);
        return new AdminInviteResponse(invited.getAdminId(), true);
    }

    @Transactional
    public void sendPasswordReset(Admin actor, Long adminId, String ipAddress) {
        if (actor.getAdminId().equals(adminId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        Admin target = adminRepository.findByIdForUpdate(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        if (!target.isActive()) {
            throw new BusinessException(ErrorCode.ADMIN_STATUS_CONFLICT);
        }
        reissueResetLink(target, actor);
        activityLogService.record(actor, AdminActivityType.ADMIN_PASSWORD_RESET_SENT,
                AdminActivityTargetType.ADMIN, target.getAdminId(), target.getName(),
                "관리자 비밀번호 재설정 메일을 발송했습니다.", ipAddress);
    }

    @Transactional
    public void requestPasswordReset(AdminPasswordResetRequest request, String ipAddress) {
        String email = normalize(request.email());
        // 계정 존재 여부가 드러나지 않도록 조회 전에 발송 횟수를 먼저 기록한다.
        rateLimitService.checkAndRecord(email, ipAddress);
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

    private void reissueResetLink(Admin target, Admin createdBy) {
        LocalDateTime now = LocalDateTime.now();
        invitationRepository.findAllByAdminAndType(target, AdminInvitationType.RESET)
                .stream().filter(invitation -> invitation.isUsable(now))
                .forEach(invitation -> invitation.invalidate(now));
        invitationIssuer.issue(target, createdBy, AdminInvitationType.RESET);
    }

    @Transactional(readOnly = true)
    public AdminInvitationVerifyResponse verify(AdminInvitationTokenRequest request) {
        AdminInvitation invitation = invitationRepository.findByTokenHash(
                        TokenHashUtil.hashHex(request.token()))
                .filter(this::valid)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITATION_TOKEN));
        return new AdminInvitationVerifyResponse(invitation.getAdmin().getEmail(), invitation.getType());
    }

    @Transactional
    public void accept(AdminInvitationAcceptRequest request) {
        AdminInvitation invitation = invitationRepository.findByTokenHashForUpdate(
                        TokenHashUtil.hashHex(request.token()))
                .filter(this::valid)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITATION_TOKEN));
        AdminPasswordPolicy.validate(request.password());
        Admin admin = invitation.getAdmin();
        if (invitation.getType() == AdminInvitationType.INVITE) {
            admin.activate(passwordEncoder.encode(request.password()));
        } else if (invitation.getType() == AdminInvitationType.RESET) {
            admin.changePassword(passwordEncoder.encode(request.password()));
            refreshTokenRepository.revokeAllByAdminId(admin.getAdminId());
        }
        invitation.use(LocalDateTime.now());
    }

    public void ensureBootstrap(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = normalize(email);
        Admin admin = adminRepository.findByEmail(normalized).orElse(null);
        if (admin == null) {
            try {
                admin = bootstrapTransactionService.createAdmin(normalized);
            } catch (DataIntegrityViolationException e) {
                admin = adminRepository.findByEmail(normalized)
                        .orElseThrow(() -> e);
            }
        }
        bootstrapTransactionService.ensureInvitation(admin.getAdminId());
    }

    private boolean valid(AdminInvitation invitation) {
        if (!invitation.isUsable(LocalDateTime.now())) {
            return false;
        }
        AdminStatus status = invitation.getAdmin().getStatus();
        if (invitation.getType() == AdminInvitationType.RESET) {
            return status == AdminStatus.ACTIVE;
        }
        return status != AdminStatus.SUSPENDED;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
