package com.example.senioron.domain.socialaccount.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoUserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoUserClientTest {

    private static final String USER_INFO_URL =
            "https://mock-kakao.example.com/v2/user/me";

    @Test
    void getUserInfoUsesConfiguredUrlAndBearerToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(builder)
                .build();
        KakaoUserClient client = new KakaoUserClient(
                builder.build(),
                USER_INFO_URL
        );

        server.expect(requestTo(USER_INFO_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer kakao-access-token"
                ))
                .andRespond(withSuccess(
                        """
                                {
                                  "id": 12345,
                                  "kakao_account": {
                                    "email": "kakao@example.com",
                                    "profile": {
                                      "nickname": "kakao-user"
                                    }
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KakaoUserInfo response = client.getUserInfo("kakao-access-token");

        assertThat(response.getId()).isEqualTo(12345L);
        assertThat(response.getEmail()).isEqualTo("kakao@example.com");
        assertThat(response.getNickname()).isEqualTo("kakao-user");
        server.verify();
    }
}
