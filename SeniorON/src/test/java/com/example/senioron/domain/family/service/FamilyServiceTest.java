package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.storage.S3Service;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FamilyServiceTest {

    private final FamilyRepository familyRepository =
            org.mockito.Mockito.mock(FamilyRepository.class);
    private final UserRepository userRepository =
            org.mockito.Mockito.mock(UserRepository.class);
    private final S3Service s3Service =
            org.mockito.Mockito.mock(S3Service.class);
    private final FamilyPhotoRepository familyPhotoRepository =
            org.mockito.Mockito.mock(FamilyPhotoRepository.class);
    private final FamilyPhotoPermissionService familyPhotoPermissionService =
            org.mockito.Mockito.mock(FamilyPhotoPermissionService.class);
    private final FamilyMemberRepository familyMemberRepository =
            org.mockito.Mockito.mock(FamilyMemberRepository.class);
    private final PhotoGroupRepository photoGroupRepository =
            org.mockito.Mockito.mock(PhotoGroupRepository.class);
    private final PhotoGroupFamilyRepository photoGroupFamilyRepository =
            org.mockito.Mockito.mock(PhotoGroupFamilyRepository.class);
    private final DeviceService deviceService =
            org.mockito.Mockito.mock(DeviceService.class);
    private final SeniorRepository seniorRepository =
            org.mockito.Mockito.mock(SeniorRepository.class);

    private FamilyService familyService;

    @BeforeEach
    void setUp() {
        familyService = new FamilyService(
                familyRepository,
                userRepository,
                s3Service,
                familyPhotoRepository,
                familyPhotoPermissionService,
                familyMemberRepository,
                photoGroupRepository,
                photoGroupFamilyRepository,
                deviceService,
                seniorRepository
        );
    }

    @Test
    void joinFamilyLinksParentUserToExistingFamilySenior() {
        Family family = Family.builder()
                .familyId(1L)
                .seniorCode("ABCD-1234")
                .build();
        User parent = User.builder()
                .usersId(2L)
                .name("부모")
                .role(Role.PARENT)
                .build();
        User child = User.builder()
                .usersId(1L)
                .name("자녀")
                .role(Role.CHILD)
                .build();
        Senior senior = Senior.builder()
                .seniorId(10L)
                .name("시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(family)
                .registeredBy(child)
                .build();

        given(userRepository.findById(2L)).willReturn(Optional.of(parent));
        given(familyRepository.findBySeniorCode("ABCD-1234")).willReturn(Optional.of(family));
        given(familyMemberRepository.existsByUserAndFamily(parent, family)).willReturn(false);
        given(seniorRepository.findAllByFamilyOrderBySeniorIdAscForUpdate(family))
                .willReturn(java.util.List.of(senior));
        given(seniorRepository.existsByParentUser(parent)).willReturn(false);

        familyService.joinFamily(parent, createJoinRequest("ABCD-1234"));

        assertThat(senior.getParentUser()).isEqualTo(parent);
        verify(deviceService).reconnectDevice(parent);
        verify(seniorRepository).saveAndFlush(senior);
    }

    private FamilyJoinRequest createJoinRequest(String seniorCode) {
        FamilyJoinRequest request = new FamilyJoinRequest();
        ReflectionTestUtils.setField(request, "seniorCode", seniorCode);
        return request;
    }
}
