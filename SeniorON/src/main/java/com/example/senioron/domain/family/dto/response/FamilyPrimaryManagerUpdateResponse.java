package com.example.senioron.domain.family.dto.response;

import com.example.senioron.domain.user.entity.ManagerType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class FamilyPrimaryManagerUpdateResponse {
    private Long usersId;
    private String name;
    private ManagerType managerType;
}
