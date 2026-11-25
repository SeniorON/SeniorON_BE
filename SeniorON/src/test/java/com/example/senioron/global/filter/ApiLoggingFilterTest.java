package com.example.senioron.global.filter;

import com.example.senioron.global.jwt.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ApiLoggingFilterTest {

    private final ApiLoggingFilter apiLoggingFilter = new ApiLoggingFilter();

    @Test
    void logsPerfWithZeroJwtAuthenticationWhenJwtAttributesAreAbsent(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (servletRequest, servletResponse) -> {
        };

        apiLoggingFilter.doFilter(request, response, filterChain);

        assertThat(output).contains("[RESPONSE] POST /api/users/login | 200 | total=");
        assertThat(output).contains("jwtAuthentication=0ms");
        assertThat(output).contains("status=200");
    }

    @Test
    void logsPerfWithJwtAuthenticationAndControllerServiceAfterJwtAttributes(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/family/home");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (servletRequest, servletResponse) -> {
            servletRequest.setAttribute(JwtAuthenticationFilter.JWT_AUTHENTICATION_TIME_ATTRIBUTE, 12L);
            servletRequest.setAttribute(JwtAuthenticationFilter.CONTROLLER_SERVICE_AFTER_JWT_TIME_ATTRIBUTE, 34L);
        };

        apiLoggingFilter.doFilter(request, response, filterChain);

        assertThat(output).contains(
                "[PERF] GET /api/family/home - request total="
        );
        assertThat(output).contains("jwtAuthentication=12ms");
        assertThat(output).contains("controller/service after JWT=34ms");
        assertThat(output).contains("status=200");
    }
}
