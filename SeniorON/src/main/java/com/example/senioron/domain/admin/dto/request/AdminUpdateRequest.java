package com.example.senioron.domain.admin.dto.request;

import com.example.senioron.domain.admin.entity.AdminRole;

public record AdminUpdateRequest(
        AdminRole adminRole,
        Boolean active
) {
    public boolean isEmpty() {
        return adminRole == null && active == null;
    }
}
