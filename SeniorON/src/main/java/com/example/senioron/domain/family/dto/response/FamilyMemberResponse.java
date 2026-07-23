package com.example.senioron.domain.family.dto.response;

import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FamilyMemberResponse {

    private Long usersId; // 나중에 주 담당자 변경/ 삭제 요청할떄 사용

    private String name;

    private Role role;

    private boolean canBecomePrimary;

    private ManagerType managerType;

    private boolean me;

    private String profileImageUrl;

}
