package com.example.senioron.domain.inactivity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;

import com.example.senioron.domain.inactivity.dto.response.InactivitySettingResponse;
import com.example.senioron.domain.inactivity.entity.InactivitySetting;
import com.example.senioron.domain.inactivity.repository.InactivitySettingRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InactivitySettingServiceTest {

    private static final Long PARENT_ID = 1L;
    private static final Long CHILD_ID = 2L;

    private final InactivitySettingRepository inactivitySettingRepository =
            org.mockito.Mockito.mock(InactivitySettingRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);

    private final InactivitySettingService service =
            new InactivitySettingService(inactivitySettingRepository, userRepository);

    // 부모(시니어) 기기가 자신의 임계 시간을 폴링으로 조회할 수 있어야 한다.
    @Test
    void parentCanFetchOwnSetting() {
        User parent = User.builder().usersId(PARENT_ID).role(Role.PARENT).build();
        given(userRepository.findById(PARENT_ID)).willReturn(Optional.of(parent));
        given(inactivitySettingRepository.findById(PARENT_ID)).willReturn(
                Optional.of(InactivitySetting.builder().user(parent).thresholdHours(6).isEnabled(true).build())
        );

        InactivitySettingResponse response = service.getMySetting(parent);

        assertThat(response.getThresholdHours()).isEqualTo(6);
        assertThat(response.getIsEnabled()).isTrue();
    }

    // 자녀 계정은 이 self-조회 API를 쓸 수 없어야 한다 (자녀는 targetUserId로 조회하는 별도 API를 쓴다).
    @Test
    void childCannotUseSelfLookup() {
        User child = User.builder().usersId(CHILD_ID).role(Role.CHILD).build();
        given(userRepository.findById(CHILD_ID)).willReturn(Optional.of(child));

        assertThatThrownBy(() -> service.getMySetting(child))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INACTIVITY_SETTING_PARENT_ONLY);
    }
}
