package com.example.senioron.global.jwt;

import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.config.SecurityErrorResponseWriter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String JWT_AUTHENTICATION_TIME_ATTRIBUTE = "jwtAuthenticationTimeMs";
    public static final String CONTROLLER_SERVICE_AFTER_JWT_TIME_ATTRIBUTE = "controllerServiceAfterJwtTimeMs";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/swagger-ui.html",
            "/api/users/signup",
            "/api/users/signup/email/verification-code",
            "/api/users/signup/email/verification-code/verify",
            "/api/users/login",
            "/api/users/token/refresh",
            "/api/users/check-login-id",
            "/api/users/account-recovery/login-id",
            "/api/users/account-recovery/password/verification-code",
            "/api/users/account-recovery/password/verification-code/verify",
            "/api/users/account-recovery/password",
            "/api/social-accounts/login/kakao",
            "/api/social-accounts/login/google",
            "/api/social-accounts/signup",
            "/api/social-accounts/login/kakao/callback"
    );

    private static final Set<String> PUBLIC_PATH_PREFIXES = Set.of(
            "/swagger-ui/",
            "/v3/api-docs/",
            "/h2-console/",
            "/actuator/"
    );

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return PUBLIC_PATHS.contains(path)
                || PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        long jwtAuthenticationStart = System.nanoTime();

        String token = resolveToken(request);

        try {
            if (token != null) {
                long jwtStart = System.nanoTime();
                Claims claims = jwtUtil.parseClaims(token);

                if (!jwtUtil.isAccessToken(claims)) {
                    throw new JwtException("Only access token can authenticate requests.");
                }

                Long usersId = jwtUtil.getUsersId(claims);
                log.debug("[TIMING] JWT 파싱: {}ms | {}",
                        elapsedMillis(jwtStart), path);

                long dbStart = System.nanoTime();
                User user = userRepository.findById(usersId)
                        .orElse(null);
                log.debug("[TIMING] JWT Filter DB조회 (findById): {}ms | {}",
                        elapsedMillis(dbStart), path);

                if (user != null && user.getStatus() == UserStatus.ACTIVE) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    Collections.emptyList()
                            );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute(
                    JWT_AUTHENTICATION_TIME_ATTRIBUTE,
                    elapsedMillis(jwtAuthenticationStart)
            );
            request.setAttribute(CONTROLLER_SERVICE_AFTER_JWT_TIME_ATTRIBUTE, 0L);
            SecurityContextHolder.clearContext();
            securityErrorResponseWriter.write(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        long jwtAuthenticationMs = elapsedMillis(jwtAuthenticationStart);
        request.setAttribute(JWT_AUTHENTICATION_TIME_ATTRIBUTE, jwtAuthenticationMs);

        long downstreamStart = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            request.setAttribute(
                    CONTROLLER_SERVICE_AFTER_JWT_TIME_ATTRIBUTE,
                    elapsedMillis(downstreamStart)
            );
            log.debug("[TIMING] JWT Authentication: {}ms | {}",
                    jwtAuthenticationMs, path);
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");

        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        return null;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
