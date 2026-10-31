package com.example.senioron.domain.admin.dto.response;

import com.example.senioron.domain.admin.entity.AdminInvitationType;

public record AdminInvitationVerifyResponse(
        String email,
        AdminInvitationType type
) { }
