package com.example.senioron.domain.user.dto.response;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OnboardingStatusResponse {

    private boolean hasFamily;
    private ManagerType managerType;
    private Role currentUserRole;
    private Long seniorId;
    private Long parentUserId;
    private boolean seniorProfileCompleted;
    private SeniorRelation relation;
    private boolean onboardingCompleted;
}
