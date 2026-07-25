package com.example.senioron.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

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

        // 모니터링 관련 요청은 제외
        if (uri.startsWith("/actuator")
                || uri.startsWith("/prometheus")
                || uri.equals("/favicon.ico")) {

            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper =
                new ContentCachingRequestWrapper(request,10240);

        ContentCachingResponseWrapper responseWrapper =
                new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();

        String queryString = request.getQueryString();
        String fullUri = queryString == null
                ? uri
                : uri + "?" + queryString;

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
                    fullUri,
                    clientIp
            );

            filterChain.doFilter(requestWrapper, responseWrapper);

        } finally {

            long duration = System.currentTimeMillis() - start;
            int status = responseWrapper.getStatus();

            if (status >= 500) {
                log.error(
                        "[RESPONSE] {} {} | {} | {}ms",
                        request.getMethod(),
                        fullUri,
                        status,
                        duration
                );
            } else {
                log.info(
                        "[RESPONSE] {} {} | {} | {}ms",
                        request.getMethod(),
                        fullUri,
                        status,
                        duration
                );
            }

            responseWrapper.copyBodyToResponse();
        }
    }
}