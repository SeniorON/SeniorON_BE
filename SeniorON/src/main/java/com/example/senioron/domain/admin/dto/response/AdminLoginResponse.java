package com.example.senioron.domain.admin.dto.response;
import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminRole;
public record AdminLoginResponse(String accessToken, String refreshToken, AdminInfo admin) {
    public static AdminLoginResponse of(String accessToken, String refreshToken, Admin admin) { return new AdminLoginResponse(accessToken, refreshToken, new AdminInfo(admin.getAdminId(), admin.getName(), admin.getAdminRole())); }
    public record AdminInfo(Long adminId, String name, AdminRole adminRole) { }
}
