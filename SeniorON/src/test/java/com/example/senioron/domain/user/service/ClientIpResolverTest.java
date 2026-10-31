package com.example.senioron.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private final ClientIpResolver clientIpResolver = new ClientIpResolver();

    @Test
    void resolveUsesXRealIpWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Real-IP", "203.0.113.1");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.1");
    }

    @Test
    void resolveUsesValidIpv6FromXRealIp() {
        MockHttpServletRequest request = requestWithRealIp("2001:db8::1");
        assertThat(clientIpResolver.resolve(request)).isEqualTo("2001:db8::1");
    }

    @Test
    void resolveRejectsHostnameFromXRealIp() {
        assertThat(clientIpResolver.resolve(requestWithRealIp("abc")))
                .isEqualTo("10.0.0.10");
    }

    @Test
    void resolveRejectsXRealIpLongerThan45Characters() {
        assertThat(clientIpResolver.resolve(requestWithRealIp("1".repeat(46))))
                .isEqualTo("10.0.0.10");
    }

    @Test
    void resolveRejectsCommaSeparatedXRealIpValues() {
        assertThat(clientIpResolver.resolve(requestWithRealIp("1.2.3.4, 5.6.7.8")))
                .isEqualTo("10.0.0.10");
    }

    @Test
    void resolveIgnoresSpoofedXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", " 203.0.113.1, 198.51.100.2, 10.0.0.10 ");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.0.0.10");
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

    private MockHttpServletRequest requestWithRealIp(String realIp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Real-IP", realIp);
        return request;
    }
}
