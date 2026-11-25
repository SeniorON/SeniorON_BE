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
        String candidate = realIp.trim();
        if (candidate.length() > 45 || !isIpLiteral(candidate)) {
            return request.getRemoteAddr();
        }
        return candidate;
    }

    private boolean isIpLiteral(String value) {
        return isIpv4(value) || isIpv6(value);
    }

    private boolean isIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (!part.matches("\\d{1,3}")) {
                return false;
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv6(String value) {
        if (!value.matches("[0-9A-Fa-f:]+") || value.indexOf(':') < 0) {
            return false;
        }
        int compression = value.indexOf("::");
        if (compression != value.lastIndexOf("::")) {
            return false;
        }
        String[] groups = value.split(":", -1);
        int nonEmptyGroups = 0;
        for (String group : groups) {
            if (!group.isEmpty()) {
                if (group.length() > 4) {
                    return false;
                }
                nonEmptyGroups++;
            }
        }
        return compression >= 0 ? nonEmptyGroups < 8 : nonEmptyGroups == 8;
    }
}
