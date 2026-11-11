package com.example.senioron.domain.socialaccount.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoTokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoAuthorizationClientTest {

    @Test
    void exchangesAuthorizationCodeUsingConfiguredWithdrawalRedirectUri() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoAuthorizationClient client = new KakaoAuthorizationClient(
                builder.build(),
                "https://mock-kakao.example.com/oauth/token",
                "rest-api-key",
                "client-secret",
                "http://localhost:5174/"
        );

        server.expect(requestTo("https://mock-kakao.example.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=authorization_code"),
                        org.hamcrest.Matchers.containsString("client_id=rest-api-key"),
                        org.hamcrest.Matchers.containsString("client_secret=client-secret"),
                        org.hamcrest.Matchers.containsString("redirect_uri=http%3A%2F%2Flocalhost%3A5174%2F"),
                        org.hamcrest.Matchers.containsString("code=authorization-code")
                )))
                .andRespond(withSuccess("""
                        {"access_token":"kakao-access-token","token_type":"bearer"}
                        """, MediaType.APPLICATION_JSON));

        KakaoTokenResponse response = client.exchangeAuthorizationCode("authorization-code");

        assertThat(response.getAccessToken()).isEqualTo("kakao-access-token");
        server.verify();
    }
}
