package com.example.senioron.domain.admin.service;

import com.example.senioron.domain.admin.dto.request.AdminUpdateRequest;
import com.example.senioron.domain.admin.dto.response.AdminPageResponse;
import com.example.senioron.domain.admin.dto.response.AdminSummaryResponse;
import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminActivityTargetType;
import com.example.senioron.domain.admin.entity.AdminActivityType;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.entity.AdminStatus;
import com.example.senioron.domain.admin.repository.AdminRefreshTokenRepository;
import com.example.senioron.domain.admin.repository.AdminRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminManagementService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort LATEST_FIRST = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("adminId")
    );

    private final AdminRepository adminRepository;
    private final AdminRefreshTokenRepository refreshTokenRepository;
    private final AdminActivityLogService activityLogService;

    @Transactional(readOnly = true)
    public AdminPageResponse search(String keyword, int page, int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), LATEST_FIRST);
        Page<Admin> admins = keyword == null || keyword.isBlank()
                ? adminRepository.findAll(pageable)
                : adminRepository.searchByNameOrEmail(likePattern(keyword), pageable);
        return AdminPageResponse.from(admins);
    }

    @Transactional
    public AdminSummaryResponse update(
            Admin actor,
            Long adminId,
            AdminUpdateRequest request,
            String ipAddress
    ) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (actor.getAdminId().equals(adminId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF);
        }

        // 활성 SUPER 행을 adminId 오름차순으로 먼저 잠가, 동시에 서로를 강등·비활성화하는 요청을 직렬화한다.
        List<Admin> activeSupers = adminRepository.findAllByAdminRoleAndStatusForUpdate(
                AdminRole.SUPER, AdminStatus.ACTIVE);
        Admin target = adminRepository.findByIdForUpdate(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));

        AdminRole nextRole = request.adminRole() == null
                ? target.getAdminRole()
                : request.adminRole();
        AdminStatus nextStatus = request.active() == null
                ? target.getStatus()
                : target.statusAfterActiveChange(request.active());
        validateActiveSuperRemains(activeSupers, target, nextRole, nextStatus);

        AdminRole previousRole = target.getAdminRole();
        if (request.adminRole() != null && target.changeRole(request.adminRole())) {
            activityLogService.record(actor, AdminActivityType.ADMIN_ROLE_CHANGED,
                    AdminActivityTargetType.ADMIN, target.getAdminId(), target.getName(),
                    "관리자 권한을 변경했습니다. (" + previousRole + " → " + nextRole + ")", ipAddress);
        }
        if (Boolean.FALSE.equals(request.active()) && target.suspend()) {
            refreshTokenRepository.revokeAllByAdminId(target.getAdminId());
            activityLogService.record(actor, AdminActivityType.ADMIN_SUSPENDED,
                    AdminActivityTargetType.ADMIN, target.getAdminId(), target.getName(),
                    "관리자 계정을 비활성화했습니다.", ipAddress);
        }
        if (Boolean.TRUE.equals(request.active()) && target.reactivate()) {
            activityLogService.record(actor, AdminActivityType.ADMIN_ACTIVATED,
                    AdminActivityTargetType.ADMIN, target.getAdminId(), target.getName(),
                    "관리자 계정을 재활성화했습니다.", ipAddress);
        }
        return AdminSummaryResponse.from(target);
    }

    private void validateActiveSuperRemains(
            List<Admin> activeSupers,
            Admin target,
            AdminRole nextRole,
            AdminStatus nextStatus
    ) {
        boolean targetRemainsActiveSuper = nextRole == AdminRole.SUPER
                && nextStatus == AdminStatus.ACTIVE;
        boolean otherActiveSuperExists = activeSupers.stream()
                .anyMatch(admin -> !admin.getAdminId().equals(target.getAdminId()));
        if (!targetRemainsActiveSuper && !otherActiveSuperExists) {
            throw new BusinessException(ErrorCode.LAST_SUPER_ADMIN);
        }
    }

    private String likePattern(String keyword) {
        String escaped = keyword.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
