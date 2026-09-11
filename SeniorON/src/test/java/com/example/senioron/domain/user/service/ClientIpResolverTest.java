package com.example.senioron.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private final ClientIpResolver clientIpResolver = new ClientIpResolver();

    @Test
    void resolveUsesXForwardedForWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.1");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.1");
    }

    @Test
    void resolveUsesFirstIpWhenXForwardedForContainsMultipleIps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", " 203.0.113.1, 198.51.100.2, 10.0.0.10 ");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.1");
    }

    @Test
    void resolveFallsBackToRemoteAddrWhenXForwardedForIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.0.0.10");
    }

    @Test
    void resolveFallsBackToRemoteAddrWhenXForwardedForIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "   ");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.0.0.10");
    }

    @Test
    void resolveFallsBackToRemoteAddrWhenFirstForwardedIpIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", " , 203.0.113.1");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.0.0.10");
    }
}
