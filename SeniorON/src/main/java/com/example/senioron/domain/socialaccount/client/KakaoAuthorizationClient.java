package com.example.senioron.domain.socialaccount.client;

import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoTokenResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class KakaoAuthorizationClient {

    private final RestClient restClient;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String withdrawalRedirectUri;

    @Autowired
    public KakaoAuthorizationClient(
            @Value("${kakao.withdrawal.token-url}") String tokenUrl,
            @Value("${kakao.client-id}") String clientId,
            @Value("${kakao.client-secret:}") String clientSecret,
            @Value("${kakao.withdrawal.redirect-uri}") String withdrawalRedirectUri,
            @Value("${kakao.user.connect-timeout}") Duration connectTimeout,
            @Value("${kakao.user.read-timeout}") Duration readTimeout
    ) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.withdrawalRedirectUri = withdrawalRedirectUri;
        this.restClient = KakaoRestClientFactory.create(connectTimeout, readTimeout);
    }

    KakaoAuthorizationClient(
            RestClient restClient,
            String tokenUrl,
            String clientId,
            String clientSecret,
            String withdrawalRedirectUri
    ) {
        this.restClient = restClient;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.withdrawalRedirectUri = withdrawalRedirectUri;
    }

    /** Exchanges a web authorization code using the exact redirect URI used by the web client. */
    public KakaoTokenResponse exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", withdrawalRedirectUri);
        form.add("code", code);
        if (StringUtils.hasText(clientSecret)) {
            form.add("client_secret", clientSecret);
        }

        return restClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);
    }
}
