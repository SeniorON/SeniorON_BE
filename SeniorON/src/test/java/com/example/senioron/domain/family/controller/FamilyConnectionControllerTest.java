package com.example.senioron.domain.family.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.family.service.FamilyConnectionService;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FamilyConnectionControllerTest {

    @Test
    void disconnectReturnsNoContent() {
        FamilyConnectionService service = mock(FamilyConnectionService.class);
        FamilyConnectionController controller =
                new FamilyConnectionController(service);
        User user = User.builder()
                .usersId(1L)
                .name("자녀")
                .role(Role.CHILD)
                .build();

        var response = controller.disconnect(user, 10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(service).disconnect(user, 10L);
    }
}
