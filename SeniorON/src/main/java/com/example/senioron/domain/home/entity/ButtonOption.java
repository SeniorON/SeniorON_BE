package com.example.senioron.domain.home.entity;

import com.example.senioron.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ButtonOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long optionId;

    private String buttonName;

    private String icon;

    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    private String actionValue;
}