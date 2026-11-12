package com.example.senioron.domain.socialaccount.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoAccessTokenInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoAccessTokenInfoClientTest {

    private static final String ACCESS_TOKEN_INFO_URL =
            "https://mock-kakao.example.com/v1/user/access_token_info";

    @Test
    void getAccessTokenInfoUsesConfiguredUrlAndBearerToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoAccessTokenInfoClient client = new KakaoAccessTokenInfoClient(
                builder.build(),
                ACCESS_TOKEN_INFO_URL
        );

        server.expect(requestTo(ACCESS_TOKEN_INFO_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-access-token"))
                .andRespond(withSuccess(
                        """
                                {
                                  "id": 12345,
                                  "expires_in": 21599,
                                  "app_id": 67890
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KakaoAccessTokenInfo response = client.getAccessTokenInfo("kakao-access-token");

        assertThat(response.getId()).isEqualTo(12345L);
        assertThat(response.getExpiresIn()).isEqualTo(21599L);
        assertThat(response.getAppId()).isEqualTo(67890L);
        server.verify();
    }
}
