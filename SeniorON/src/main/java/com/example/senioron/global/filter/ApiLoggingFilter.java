package com.example.senioron.global.filter;

import com.example.senioron.global.jwt.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // 모니터링 관련 요청 제외
        if (uri.startsWith("/actuator")
                || uri.startsWith("/prometheus")
                || uri.equals("/favicon.ico")) {

            filterChain.doFilter(request, response);
            return;
        }

        long start = System.nanoTime();

        // QueryString은 기록하지 않음 (민감정보 노출 방지)
        String requestUri = request.getRequestURI();

        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        } else {
            clientIp = clientIp.split(",")[0].trim();
        }

        try {

            log.info(
                    "[REQUEST] {} {} | IP={}",
                    request.getMethod(),
                    requestUri,
                    clientIp
            );

            // ApiLoggingFilter 전처리 시간 (Security Filter 진입 전까지)
            long preChainMs = elapsedMillis(start);
            log.debug("[TIMING] pre-chain (Security 진입 전): {}ms | {} {}",
                    preChainMs, request.getMethod(), requestUri);

            long chainStart = System.nanoTime();
            filterChain.doFilter(request, response);
            long chainMs = elapsedMillis(chainStart);

            log.debug("[TIMING] chain (Security+Service+Tx): {}ms | {} {}",
                    chainMs, request.getMethod(), requestUri);

        } catch (Exception e) {

            long duration = elapsedMillis(start);

            log.error(
                    "[EXCEPTION] {} {} | {}ms",
                    request.getMethod(),
                    requestUri,
                    duration,
                    e
            );

            throw e;

        } finally {

            long duration = elapsedMillis(start);
            int status = response.getStatus();
            long jwtAuthenticationMs = getLongAttribute(
                    request,
                    JwtAuthenticationFilter.JWT_AUTHENTICATION_TIME_ATTRIBUTE,
                    0L
            );
            long controllerServiceAfterJwtMs = getLongAttribute(
                    request,
                    JwtAuthenticationFilter.CONTROLLER_SERVICE_AFTER_JWT_TIME_ATTRIBUTE,
                    Math.max(0L, duration - jwtAuthenticationMs)
            );

            if (status >= 500) {
                log.error(
                        "[RESPONSE] {} {} | {} | total={}ms",
                        request.getMethod(),
                        requestUri,
                        status,
                        duration
                );
            } else {
                log.info(
                        "[RESPONSE] {} {} | {} | total={}ms",
                        request.getMethod(),
                        requestUri,
                        status,
                        duration
                );
            }

            log.info(
                    "[PERF] {} {} - request total={}ms jwtAuthentication={}ms controller/service after JWT={}ms status={}",
                    request.getMethod(),
                    requestUri,
                    duration,
                    jwtAuthenticationMs,
                    controllerServiceAfterJwtMs,
                    status
            );
        }
    }

    private long getLongAttribute(HttpServletRequest request, String attributeName, long defaultValue) {
        Object value = request.getAttribute(attributeName);

        if (value instanceof Long longValue) {
            return longValue;
        }

        return defaultValue;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
