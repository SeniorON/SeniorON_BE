package com.example.senioron.domain.user.dto.response;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonPropertyOrder({
        "currentUserRole",
        "hasFamily",
        "onboardingCompleted",
        "families"
})
public class OnboardingStatusResponse {

    private Role currentUserRole;
    private boolean hasFamily;
    private boolean onboardingCompleted;
    private List<FamilyStatus> families;

    @Getter
    @Builder
    @JsonPropertyOrder({
            "familyId",
            "managerType",
            "parentUserId",
            "relation",
            "seniorId",
            "seniorName",
            "seniorProfileCompleted"
    })
    public static class FamilyStatus {

        private Long familyId;
        private ManagerType managerType;
        private Long parentUserId;
        private SeniorRelation relation;
        private Long seniorId;
        private String seniorName;
        private boolean seniorProfileCompleted;
    }
}
