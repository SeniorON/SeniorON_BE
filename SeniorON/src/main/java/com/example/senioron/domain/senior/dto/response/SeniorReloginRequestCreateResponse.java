package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.SeniorReloginRequest;
import com.example.senioron.domain.senior.entity.SeniorReloginRequestStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SeniorReloginRequestCreateResponse {

    private Long seniorReloginRequestId;
    private Long seniorId;
    private Long deviceId;
    private SeniorReloginRequestStatus status;
    private LocalDateTime expiresAt;

    public static SeniorReloginRequestCreateResponse from(SeniorReloginRequest request) {
        return SeniorReloginRequestCreateResponse.builder()
                .seniorReloginRequestId(request.getSeniorReloginRequestId())
                .seniorId(request.getSenior().getSeniorId())
                .deviceId(request.getDevice().getDeviceId())
                .status(request.getStatus())
                .expiresAt(request.getExpiresAt())
                .build();
    }
}
