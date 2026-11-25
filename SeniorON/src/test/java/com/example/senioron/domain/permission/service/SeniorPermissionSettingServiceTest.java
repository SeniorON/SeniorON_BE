package com.example.senioron.domain.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.permission.dto.request.SeniorPermissionSettingUpdateRequest;
import com.example.senioron.domain.permission.dto.response.SeniorPermissionSettingResponse;
import com.example.senioron.domain.permission.entity.SeniorPermissionSetting;
import com.example.senioron.domain.permission.repository.SeniorPermissionSettingRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SeniorPermissionSettingServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SENIOR_ID = 2L;

    private final SeniorPermissionSettingRepository settingRepository =
            org.mockito.Mockito.mock(SeniorPermissionSettingRepository.class);
    private final SeniorRepository seniorRepository = org.mockito.Mockito.mock(SeniorRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final FamilyMemberRepository familyMemberRepository =
            org.mockito.Mockito.mock(FamilyMemberRepository.class);

    private final SeniorPermissionSettingService service =
            new SeniorPermissionSettingService(
                    settingRepository,
                    seniorRepository,
                    userRepository,
                    familyMemberRepository
            );

    @Test
    void familyMemberCanReadSeniorPermissionSetting() {
        User user = User.builder().usersId(USER_ID).role(Role.CHILD).build();
        Family family = Family.builder().build();
        Senior senior = Senior.builder().seniorId(SENIOR_ID).family(family).build();
        SeniorPermissionSetting setting = SeniorPermissionSetting.builder()
                .seniorId(SENIOR_ID)
                .senior(senior)
                .locationEnabled(false)
                .inactivityDetectionEnabled(true)
                .build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(seniorRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(user, family)).willReturn(true);
        given(settingRepository.findById(SENIOR_ID)).willReturn(Optional.of(setting));

        SeniorPermissionSettingResponse response = service.getSetting(user, SENIOR_ID);

        assertThat(response.seniorId()).isEqualTo(SENIOR_ID);
        assertThat(response.locationEnabled()).isFalse();
        assertThat(response.inactivityDetectionEnabled()).isTrue();
    }

    @Test
    void nonFamilyMemberCannotReadSeniorPermissionSetting() {
        User user = User.builder().usersId(USER_ID).role(Role.CHILD).build();
        Family family = Family.builder().build();
        Senior senior = Senior.builder().seniorId(SENIOR_ID).family(family).build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(seniorRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(user, family)).willReturn(false);

        assertThatThrownBy(() -> service.getSetting(user, SENIOR_ID))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(settingRepository);
    }

    @Test
    void parentCanUpdateOwnSeniorPermissionSetting() {
        User parent = User.builder().usersId(USER_ID).role(Role.PARENT).build();
        Senior senior = Senior.builder().seniorId(SENIOR_ID).parentUser(parent).build();
        SeniorPermissionSetting setting = SeniorPermissionSetting.builder()
                .seniorId(SENIOR_ID)
                .senior(senior)
                .locationEnabled(true)
                .inactivityDetectionEnabled(false)
                .build();
        SeniorPermissionSettingUpdateRequest request =
                SeniorPermissionSettingUpdateRequest.builder()
                        .locationEnabled(false)
                        .build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(parent));
        given(seniorRepository.findByParentUser(parent)).willReturn(Optional.of(senior));
        given(settingRepository.findById(SENIOR_ID)).willReturn(Optional.of(setting));

        SeniorPermissionSettingResponse response = service.updateMySetting(parent, request);

        assertThat(response.seniorId()).isEqualTo(SENIOR_ID);
        assertThat(response.locationEnabled()).isFalse();
        assertThat(response.inactivityDetectionEnabled()).isFalse();
        verify(seniorRepository).findByParentUser(parent);
    }

    @Test
    void childCannotUpdatePermissionSetting() {
        User child = User.builder().usersId(USER_ID).role(Role.CHILD).build();
        SeniorPermissionSettingUpdateRequest request =
                SeniorPermissionSettingUpdateRequest.builder()
                        .locationEnabled(false)
                        .build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(child));

        assertThatThrownBy(() -> service.updateMySetting(child, request))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_PERMISSION_SETTING_PARENT_ONLY);
        verifyNoInteractions(settingRepository, seniorRepository, familyMemberRepository);
    }

    @Test
    void parentWithoutLinkedSeniorCannotUpdatePermissionSetting() {
        User parent = User.builder().usersId(USER_ID).role(Role.PARENT).build();
        SeniorPermissionSettingUpdateRequest request =
                SeniorPermissionSettingUpdateRequest.builder()
                        .locationEnabled(false)
                        .build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(parent));
        given(seniorRepository.findByParentUser(parent)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMySetting(parent, request))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_NOT_FOUND);
        verifyNoInteractions(settingRepository, familyMemberRepository);
    }

    @Test
    void emptyUpdateRequestIsRejected() {
        User parent = User.builder().usersId(USER_ID).role(Role.PARENT).build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.updateMySetting(
                parent,
                SeniorPermissionSettingUpdateRequest.builder().build()
        ))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_PERMISSION_SETTING_UPDATE_EMPTY);
        verifyNoInteractions(settingRepository, seniorRepository, familyMemberRepository);
    }
}
