package com.example.senioron.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.example.senioron.domain.admin.entity.*;
import com.example.senioron.domain.admin.repository.AdminRepository;
import com.example.senioron.domain.user.entity.*;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.config.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminJwtAuthenticationFilterTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final JwtUtil jwt = new JwtUtil("user-secret-key-12345678901234567890", 3600000, 1209600000,
            "admin-secret-key-12345678901234567890", 1800000, 86400000);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users, admins, new SecurityErrorResponseWriter());

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void userTokenCannotAuthenticateAdminPath() throws Exception { assertRejected(jwt.createAccessToken(user()), "/api/admin/me"); }
    @Test void adminRefreshCannotAuthenticateAdminPath() throws Exception { assertRejected(jwt.createAdminRefreshToken(admin(AdminStatus.ACTIVE)), "/api/admin/me"); }
    @Test void adminTokenCannotAuthenticateUserPath() throws Exception { assertRejected(jwt.createAdminAccessToken(admin(AdminStatus.ACTIVE)), "/api/users/me"); }

    @Test void activeSuperAdminGetsBothAuthorities() throws Exception {
        Admin admin = admin(AdminStatus.ACTIVE);
        given(admins.findById(2L)).willReturn(Optional.of(admin));
        MockHttpServletRequest request = request(jwt.createAdminAccessToken(admin), "/api/admin/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, mock(FilterChain.class));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    }

    private void assertRejected(String token, String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request(token, path), response, mock(FilterChain.class));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    private MockHttpServletRequest request(String token, String path) { MockHttpServletRequest r = new MockHttpServletRequest("GET", path); r.setRequestURI(path); r.addHeader("Authorization", "Bearer " + token); return r; }
    private User user() { return User.builder().usersId(1L).loginId("user").email("user@example.com").password("p").name("user").role(Role.PARENT).status(UserStatus.ACTIVE).build(); }
    private Admin admin(AdminStatus status) { return Admin.builder().adminId(2L).email("admin@example.com").password("p").name("admin").adminRole(AdminRole.SUPER).status(status).build(); }
}
