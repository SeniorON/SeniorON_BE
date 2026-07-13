package com.example.senioron.domain.event.util;

import com.example.senioron.domain.event.dto.response.KakaoAddressResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeocodingClient {
    private final RestClient kakaoRestClient;

    @Value("${kakao.local.api-key:}")
    private String kakaoApiKey;

    public String reverseGeocode(BigDecimal lat, BigDecimal lng){
        try{
            KakaoAddressResponse response = kakaoRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/geo/coord2address.json")
                            .queryParam("x", lng)
                            .queryParam("y", lat)
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .body(KakaoAddressResponse.class);

            if(response == null || response.documents().isEmpty()){
                return "위치 정보를 확인할 수 없어요";
            }

            KakaoAddressResponse.Document document = response.documents().get(0);

            KakaoAddressResponse.Address address = document.address();
            if (address != null && address.addressName() != null && !address.addressName().isBlank()) {
                return address.addressName();
            }

            KakaoAddressResponse.RoadAddress roadAddress = document.roadAddress();
            if (roadAddress != null && roadAddress.roadAddressName() != null && !roadAddress.roadAddressName().isBlank()) {
                return roadAddress.roadAddressName();
            }

            return "위치정보를 확인할 수 없어요";

        } catch (Exception e){
            log.warn("카카오 역지오코딩 실패: {}", e.getMessage());
            return "위치정보를 확인할 수 없어요";
        }
    }
}
