package com.example.senioron.domain.admin.dto.response;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.entity.AdminStatus;
import java.time.LocalDateTime;

public record AdminSummaryResponse(
        Long adminId,
        String name,
        String email,
        AdminRole adminRole,
        AdminStatus status,
        LocalDateTime lastLoginAt
) {
    public static AdminSummaryResponse from(Admin admin) {
        return new AdminSummaryResponse(
                admin.getAdminId(),
                admin.getName(),
                admin.getEmail(),
                admin.getAdminRole(),
                admin.getStatus(),
                admin.getLastLoginAt()
        );
    }
}
