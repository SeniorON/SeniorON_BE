package com.example.senioron.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
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

        long start = System.currentTimeMillis();

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
            long preChainMs = System.currentTimeMillis() - start;
            log.debug("[TIMING] pre-chain (Security 진입 전): {}ms | {} {}",
                    preChainMs, request.getMethod(), requestUri);

            long chainStart = System.currentTimeMillis();
            filterChain.doFilter(request, response);
            long chainMs = System.currentTimeMillis() - chainStart;

            log.debug("[TIMING] chain (Security+Service+Tx): {}ms | {} {}",
                    chainMs, request.getMethod(), requestUri);

        } catch (Exception e) {

            long duration = System.currentTimeMillis() - start;

            log.error(
                    "[EXCEPTION] {} {} | {}ms",
                    request.getMethod(),
                    requestUri,
                    duration,
                    e
            );

            throw e;

        } finally {

            long duration = System.currentTimeMillis() - start;
            int status = response.getStatus();

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
        }
    }
}