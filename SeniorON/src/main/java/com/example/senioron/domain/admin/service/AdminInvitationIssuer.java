package com.example.senioron.domain.admin.service;

import com.example.senioron.domain.admin.entity.*;
import com.example.senioron.domain.admin.event.AdminInvitationMailEvent;
import com.example.senioron.domain.admin.repository.AdminInvitationRepository;
import com.example.senioron.global.jwt.TokenHashUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminInvitationIssuer {
    public static final long EXPIRATION_HOURS = 48;
    public static final long RESET_EXPIRATION_HOURS = 1;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final AdminInvitationRepository repository;
    private final ApplicationEventPublisher publisher;
    @Value("${admin.web.base-url}") private String baseUrl;

    public void issue(Admin admin, Admin createdBy) {
        issue(admin, createdBy, AdminInvitationType.INVITE);
    }

    public void issue(Admin admin, Admin createdBy, AdminInvitationType type) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expirationHours = type == AdminInvitationType.RESET ? RESET_EXPIRATION_HOURS : EXPIRATION_HOURS;
        repository.save(AdminInvitation.builder().admin(admin).type(type)
                .tokenHash(TokenHashUtil.hashHex(token))
                .expiresAt(LocalDateTime.now().plusHours(expirationHours))
                .createdBy(createdBy).build());
        publisher.publishEvent(new AdminInvitationMailEvent(
                admin.getEmail(), baseUrl + "/invitation?token=" + token, type));
    }
}
