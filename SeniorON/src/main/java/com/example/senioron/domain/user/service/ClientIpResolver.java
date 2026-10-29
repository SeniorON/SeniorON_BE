package com.example.senioron.domain.user.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    private static final String X_REAL_IP = "X-Real-IP";

    public String resolve(HttpServletRequest request) {
        String realIp = request.getHeader(X_REAL_IP);
        if (realIp == null || realIp.isBlank()) {
            return request.getRemoteAddr();
        }
        return realIp.trim();
    }
}
