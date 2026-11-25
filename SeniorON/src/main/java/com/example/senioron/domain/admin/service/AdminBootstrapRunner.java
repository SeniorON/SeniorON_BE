package com.example.senioron.domain.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AdminInvitationService invitationService;

    @Value("${admin.bootstrap.email:}")
    private String bootstrapEmail;

    @Override
    public void run(ApplicationArguments args) {
        try {
            invitationService.ensureBootstrap(bootstrapEmail);
        } catch (RuntimeException e) {
            log.error("최초 SUPER 관리자 부트스트랩에 실패했습니다.", e);
        }
    }
}
