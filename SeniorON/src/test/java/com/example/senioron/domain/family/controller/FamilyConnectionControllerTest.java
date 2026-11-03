package com.example.senioron.domain.family.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.family.dto.response.FamilyConnectionDisconnectResponse;
import com.example.senioron.domain.family.service.FamilyConnectionService;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FamilyConnectionControllerTest {

    @Test
    void disconnectReturnsOkWithActualServiceResult() {
        FamilyConnectionService service = mock(FamilyConnectionService.class);
        FamilyConnectionController controller =
                new FamilyConnectionController(service);
        User user = User.builder()
                .usersId(1L)
                .name("자녀")
                .role(Role.CHILD)
                .build();
        FamilyConnectionDisconnectResponse result =
                new FamilyConnectionDisconnectResponse(10L, true);
        given(service.disconnect(user, 10L)).willReturn(result);

        var response = controller.disconnect(user, 10L);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getCode()).isEqualTo("COMMON_200");
        assertThat(response.getData()).isSameAs(result);
        assertThat(response.getData().familyId()).isEqualTo(10L);
        assertThat(response.getData().familyDeleted()).isTrue();
        verify(service).disconnect(user, 10L);
    }
}
