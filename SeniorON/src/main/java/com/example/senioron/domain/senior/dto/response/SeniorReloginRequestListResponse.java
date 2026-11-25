package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.SeniorReloginRequest;
import com.example.senioron.domain.senior.entity.SeniorReloginRequestStatus;
import java.time.LocalDateTime;

public record SeniorReloginRequestListResponse(
        Long seniorReloginRequestId,
        Long seniorId,
        String seniorName,
        Long deviceId,
        SeniorReloginRequestStatus status,
        LocalDateTime expiresAt,
        LocalDateTime requestedAt
) {

    public static SeniorReloginRequestListResponse from(SeniorReloginRequest request) {
        return new SeniorReloginRequestListResponse(
                request.getSeniorReloginRequestId(),
                request.getSenior().getSeniorId(),
                request.getSenior().getName(),
                request.getDevice().getDeviceId(),
                request.getStatus(),
                request.getExpiresAt(),
                request.getCreatedAt()
        );
    }
}