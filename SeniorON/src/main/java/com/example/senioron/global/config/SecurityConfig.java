package com.example.senioron.global.config;

import com.example.senioron.global.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 인증 실패(401·403) 응답에도 CORS 헤더가 붙도록 Security 필터 체인에서 CORS를 처리한다.
    // 환경변수 APP_CORS_ALLOWED_ORIGINS(쉼표 구분)로 덮어쓸 수 있다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseAllowedOrigins(allowedOrigins));
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    static List<String> parseAllowedOrigins(String allowedOrigins) {
        if (allowedOrigins == null) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 시니어 재로그인 요청 생성은 JWT 없이 호출 가능
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/seniors/relogin-requests"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/seniors/relogin-requests/*/reactivate"
                        ).permitAll()
                        // 브라우저는 STOMP CONNECT의 Authorization 헤더로 인증한다.
                        .requestMatchers(HttpMethod.GET, "/ws").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
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
                                "/api/social-accounts/login/kakao/callback",
                                "/api/social-accounts/login/kakao/withdrawal",
                                "/api/admin/auth/login",
                                "/api/admin/auth/token/refresh",
                                "/api/admin/auth/invitations/verify",
                                "/api/admin/auth/invitations/accept",
                                "/api/admin/auth/password-reset",
                                "/h2-console/**",
                                "/actuator/health/readiness",
                                "/actuator/prometheus"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
