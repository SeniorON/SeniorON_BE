package com.example.senioron.domain.event.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KakaoAddressResponse(List<Document> documents){
    public record Document(Address address, RoadAddress roadAddress){}
    public record Address(
            @JsonProperty("address_name") String addressName
    ){}
    public record RoadAddress(
            @JsonProperty("address_name") String roadAddressName
    ){}
}
