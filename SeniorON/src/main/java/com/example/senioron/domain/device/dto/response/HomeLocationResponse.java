package com.example.senioron.domain.device.dto.response;

import com.example.senioron.domain.senior.entity.Senior;

public record HomeLocationResponse(
        Double latitude,
        Double longitude
) {

    public static HomeLocationResponse from(Senior senior) {
        return new HomeLocationResponse(
                senior.getLatitude(),
                senior.getLongitude()
        );
    }
}