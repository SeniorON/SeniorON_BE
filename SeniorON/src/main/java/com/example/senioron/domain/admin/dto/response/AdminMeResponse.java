package com.example.senioron.domain.admin.dto.response;
import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminRole;
public record AdminMeResponse(Long adminId, String email, String name, AdminRole adminRole) {
    public static AdminMeResponse from(Admin admin) { return new AdminMeResponse(admin.getAdminId(), admin.getEmail(), admin.getName(), admin.getAdminRole()); }
}
