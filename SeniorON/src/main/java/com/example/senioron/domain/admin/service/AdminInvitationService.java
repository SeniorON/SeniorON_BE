package com.example.senioron.domain.admin.service;

import com.example.senioron.domain.admin.dto.request.*;
import com.example.senioron.domain.admin.dto.response.*;
import com.example.senioron.domain.admin.entity.*;
import com.example.senioron.domain.admin.repository.*;
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

    @Transactional
    public AdminInviteResponse invite(Admin actor, AdminInviteRequest request, String ipAddress) {
        String email = normalize(request.email());
        if (adminRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ADMIN_EMAIL);
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
        return new AdminInviteResponse(invited.getAdminId());
    }

    @Transactional
    public void resend(Admin actor, Long adminId, String ipAddress) {
        Admin invited = adminRepository.findByIdForUpdate(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        if (invited.getStatus() != AdminStatus.INVITED) {
            throw new BusinessException(ErrorCode.ADMIN_STATUS_CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        invitationRepository.findAllByAdminAndType(invited, AdminInvitationType.INVITE)
                .stream().filter(invitation -> invitation.isUsable(now))
                .forEach(invitation -> invitation.invalidate(now));
        invitationIssuer.issue(invited, actor);
        activityLogService.record(actor, AdminActivityType.ADMIN_INVITATION_RESENT,
                AdminActivityTargetType.ADMIN, invited.getAdminId(), invited.getName(),
                "관리자 초대 링크를 재발송했습니다.", ipAddress);
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
        validatePassword(request.password());
        AdminInvitation invitation = invitationRepository.findByTokenHashForUpdate(
                        TokenHashUtil.hashHex(request.token()))
                .filter(this::valid)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITATION_TOKEN));
        if (invitation.getType() == AdminInvitationType.INVITE) {
            invitation.getAdmin().activate(passwordEncoder.encode(request.password()));
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
        return invitation.isUsable(LocalDateTime.now())
                && invitation.getAdmin().getStatus() != AdminStatus.SUSPENDED;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 64
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_PASSWORD);
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
