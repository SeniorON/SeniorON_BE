package com.example.senioron.domain.admin.service;
import com.example.senioron.domain.admin.entity.*;
import com.example.senioron.domain.admin.repository.AdminActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class AdminActivityLogService {

    private final AdminActivityLogRepository repository;

    public void record(
            Admin admin,
            AdminActivityType type,
            AdminActivityTargetType targetType,
            Long targetId,
            String targetName,
            String message,
            String ipAddress
    ) {
        repository.save(AdminActivityLog.builder()
                .admin(admin)
                .activityType(type)
                .targetType(targetType)
                .targetId(targetId)
                .targetName(targetName)
                .message(message)
                .ipAddress(ipAddress)
                .build());
    }
}
