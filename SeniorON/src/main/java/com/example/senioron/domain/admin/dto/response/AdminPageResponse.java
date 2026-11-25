package com.example.senioron.domain.admin.dto.response;

import com.example.senioron.domain.admin.entity.Admin;
import java.util.List;
import org.springframework.data.domain.Page;

public record AdminPageResponse(
        List<AdminSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static AdminPageResponse from(Page<Admin> admins) {
        return new AdminPageResponse(
                admins.getContent().stream().map(AdminSummaryResponse::from).toList(),
                admins.getNumber(),
                admins.getSize(),
                admins.getTotalElements(),
                admins.getTotalPages()
        );
    }
}
