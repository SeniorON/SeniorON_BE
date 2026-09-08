package com.example.senioron.global.jwt;

import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.config.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private static final String JWT_SECRET = "12345678901234567890123456789012";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SecurityErrorResponseWriter securityErrorResponseWriter = new SecurityErrorResponseWriter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAccessTokenWithSingleClaimsParsing() throws Exception {
        JwtUtil jwtUtil = spy(new JwtUtil(JWT_SECRET, 3600000L, 1209600000L));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtUtil,
                userRepository,
                securityErrorResponseWriter
        );
        User user = createUser();
        String token = jwtUtil.createAccessToken(user);

        given(userRepository.findById(user.getUsersId())).willReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtUtil, times(1)).parseClaims(token);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(user);
        verify(filterChain).doFilter(request, response);
    }

    private User createUser() {
        return User.builder()
                .usersId(1L)
                .loginId("testId")
                .email("test@example.com")
                .password("encoded-password")
                .name("test")
                .role(Role.PARENT)
                .build();
    }
}
