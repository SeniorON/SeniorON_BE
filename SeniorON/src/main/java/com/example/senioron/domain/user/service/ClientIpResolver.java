package com.example.senioron.domain.user.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }

        String clientIp = forwardedFor.split(",")[0].trim();
        if (clientIp.isBlank()) {
            return request.getRemoteAddr();
        }

        return clientIp;
    }
}
