package com.example.senioron.domain.admin.event;

import com.example.senioron.domain.admin.entity.AdminInvitationType;

public record AdminInvitationMailEvent(String email, String link, AdminInvitationType type) {
    public AdminInvitationMailEvent(String email, String link) {
        this(email, link, AdminInvitationType.INVITE);
    }
}
