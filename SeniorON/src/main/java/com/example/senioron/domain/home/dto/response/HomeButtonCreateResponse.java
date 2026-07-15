package com.example.senioron.domain.home.dto.response;

import com.example.senioron.domain.home.entity.Home;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HomeButtonCreateResponse {

    private Long buttonId;
    private Integer buttonOrder;
    private String buttonName;
    private String icon;

    public static HomeButtonCreateResponse from(Home home) {

        return new HomeButtonCreateResponse(
                home.getHomeId(),
                home.getButtonOrder(),
                home.getButtonName(),
                home.getIcon()
        );
    }
}