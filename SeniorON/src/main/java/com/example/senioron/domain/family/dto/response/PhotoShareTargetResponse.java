package com.example.senioron.domain.family.dto.response;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhotoShareTargetResponse {

    private Long seniorId;
    private String name;
    private SeniorRelation relation;
    private String customRelation;
}