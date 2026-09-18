package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.dto.request.FamilyPrimaryManagerUpdateRequest;
import com.example.senioron.domain.family.dto.request.PhotoGroupConnectRequest;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoViewRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
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
    private final FamilyPhotoViewRepository familyPhotoViewRepository =
            org.mockito.Mockito.mock(FamilyPhotoViewRepository.class);
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
                familyPhotoViewRepository,
                familyPhotoPermissionService,
                familyMemberRepository,
                photoGroupRepository,
                photoGroupFamilyRepository,
                deviceService,
                seniorRepository
        );
    }

    @Test
    void getFamilyHomeCalculatesNewPhotoForCurrentParent() {
        Family family = Family.builder()
                .familyId(1L)
                .build();
        User parent = User.builder()
                .usersId(1L)
                .name("부모")
                .role(Role.PARENT)
                .build();
        User uploader = User.builder()
                .usersId(2L)
                .name("자녀")
                .role(Role.CHILD)
                .build();
        Senior senior = Senior.builder()
                .seniorId(10L)
                .family(family)
                .build();
        PhotoGroup defaultPhotoGroup = PhotoGroup.builder()
                .id(100L)
                .name("Family 1")
                .build();
        PhotoGroupFamily defaultGroupLink = PhotoGroupFamily.builder()
                .family(family)
                .photoGroup(defaultPhotoGroup)
                .build();
        FamilyPhoto photo = FamilyPhoto.builder()
                .familyPhotoId(30L)
                .photoGroup(defaultPhotoGroup)
                .user(uploader)
                .imageKey("photo.jpg")
                .build();
        ReflectionTestUtils.setField(
                photo,
                "createdAt",
                LocalDateTime.now()
        );

        given(userRepository.findById(1L))
                .willReturn(Optional.of(parent));
        given(seniorRepository.findById(10L))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(parent, family))
                .willReturn(true);
        given(photoGroupFamilyRepository.findFirstByFamilyOrderByIdAsc(family))
                .willReturn(Optional.of(defaultGroupLink));
        given(familyMemberRepository.findAllByFamilyOrderByIdAsc(family))
                .willReturn(List.of());
        given(familyPhotoRepository.findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
                family,
                PageRequest.of(0, 4)
        )).willReturn(List.of(photo));
        given(familyPhotoRepository.findRecentUploaders(
                family,
                PageRequest.of(0, 3)
        )).willReturn(List.of(uploader));
        given(familyPhotoViewRepository.findViewedFamilyPhotoIds(
                1L,
                List.of(30L)
        )).willReturn(List.of(), List.of(30L));

        var beforeViewed = familyService.getFamilyHome(parent, 10L);
        var afterViewed = familyService.getFamilyHome(parent, 10L);

        assertThat(beforeViewed.getRecentPhotos())
                .singleElement()
                .satisfies(item ->
                        assertThat(item.isNewPhoto()).isTrue()
                );
        assertThat(afterViewed.getRecentPhotos())
                .singleElement()
                .satisfies(item ->
                        assertThat(item.isNewPhoto()).isFalse()
                );
    }

    @Test
    void getFamilyHomeReturnsDefaultPhotoGroupId() {
        Family family = Family.builder()
                .familyId(1L)
                .build();
        User currentUser = User.builder()
                .usersId(1L)
                .name("자녀")
                .role(Role.CHILD)
                .build();
        Senior senior = Senior.builder()
                .seniorId(10L)
                .family(family)
                .build();
        PhotoGroup defaultPhotoGroup = PhotoGroup.builder()
                .id(100L)
                .name("Family 1")
                .build();
        PhotoGroupFamily defaultGroupLink = PhotoGroupFamily.builder()
                .family(family)
                .photoGroup(defaultPhotoGroup)
                .build();

        given(userRepository.findById(1L))
                .willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, family))
                .willReturn(true);
        given(photoGroupFamilyRepository.findFirstByFamilyOrderByIdAsc(family))
                .willReturn(Optional.of(defaultGroupLink));
        given(familyMemberRepository.findAllByFamilyOrderByIdAsc(family))
                .willReturn(List.of());
        given(familyPhotoRepository.findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
                family,
                PageRequest.of(0, 4)
        )).willReturn(List.of());
        given(familyPhotoRepository.findRecentUploaders(
                family,
                PageRequest.of(0, 3)
        )).willReturn(List.of());

        var response = familyService.getFamilyHome(currentUser, 10L);

        assertThat(response.getPhotoGroupId()).isEqualTo(100L);
    }

    @Test
    void getFamilyHomeRejectsFamilyWithoutDefaultPhotoGroup() {
        Family family = Family.builder()
                .familyId(1L)
                .build();
        User currentUser = User.builder()
                .usersId(1L)
                .name("자녀")
                .role(Role.CHILD)
                .build();
        Senior senior = Senior.builder()
                .seniorId(10L)
                .family(family)
                .build();

        given(userRepository.findById(1L))
                .willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, family))
                .willReturn(true);
        given(photoGroupFamilyRepository.findFirstByFamilyOrderByIdAsc(family))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.getFamilyHome(currentUser, 10L))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PHOTO_GROUP_NOT_FOUND);

        verify(familyMemberRepository, never())
                .findAllByFamilyOrderByIdAsc(family);
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
                .willReturn(List.of(senior));
        given(seniorRepository.existsByParentUser(parent)).willReturn(false);

        familyService.joinFamily(parent, createJoinRequest("ABCD-1234"));

        assertThat(senior.getParentUser()).isEqualTo(parent);
        verify(deviceService).reconnectDevice(parent);
        verify(seniorRepository).saveAndFlush(senior);
    }

    @Test
    void getFamilyMembersReturnsMembersOfSelectedSeniorFamily() {
        Family firstFamily = Family.builder()
                .familyId(1L)
                .build();
        Family selectedFamily = Family.builder()
                .familyId(2L)
                .build();
        User currentUser = createUser(1L, "현재 사용자");
        currentUser.updateFamily(firstFamily);
        User otherUser = createUser(2L, "다른 구성원");
        Senior selectedSenior = Senior.builder()
                .seniorId(20L)
                .family(selectedFamily)
                .build();
        FamilyMember currentMember = createMember(
                1L,
                currentUser,
                selectedFamily,
                ManagerType.PRIMARY
        );
        FamilyMember otherMember = createMember(
                2L,
                otherUser,
                selectedFamily,
                ManagerType.SUB
        );

        given(seniorRepository.findById(20L))
                .willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, selectedFamily))
                .willReturn(true);
        given(familyMemberRepository.findAllByFamilyOrderByIdAsc(selectedFamily))
                .willReturn(List.of(otherMember, currentMember));

        var responses = familyService.getFamilyMembers(currentUser, 20L);

        assertThat(responses)
                .extracting(response -> response.getUsersId())
                .containsExactly(1L, 2L);
        assertThat(responses.get(0).isMe()).isTrue();
        verify(familyMemberRepository)
                .findAllByFamilyOrderByIdAsc(selectedFamily);
        verify(familyMemberRepository, never())
                .findAllByFamilyOrderByIdAsc(firstFamily);
    }

    @Test
    void connectPhotoGroupCreatesNewGroupWithCurrentAndTargetFamilies() {
        Family currentFamily = Family.builder()
                .familyId(1L)
                .seniorCode("CURRENT1")
                .build();
        Family targetFamily = Family.builder()
                .familyId(2L)
                .seniorCode("TARGET-1")
                .build();
        User currentUser = createUser(1L, "주 담당자");
        Senior currentSenior = Senior.builder()
                .seniorId(10L)
                .family(currentFamily)
                .build();
        Senior targetSenior = Senior.builder()
                .seniorId(20L)
                .family(targetFamily)
                .build();
        FamilyMember primaryMember = createMember(
                1L,
                currentUser,
                currentFamily,
                ManagerType.PRIMARY
        );
        PhotoGroup savedPhotoGroup = PhotoGroup.builder()
                .id(100L)
                .name("Shared Family 1-2")
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(currentSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, currentFamily))
                .willReturn(true);
        given(familyMemberRepository.findByUserAndFamilyForUpdate(currentUser, currentFamily))
                .willReturn(Optional.of(primaryMember));
        given(familyRepository.findBySeniorCode("TARGET-1"))
                .willReturn(Optional.of(targetFamily));
        given(seniorRepository.findFirstByFamilyOrderBySeniorIdAsc(targetFamily))
                .willReturn(Optional.of(targetSenior));
        given(photoGroupFamilyRepository.existsSharedPhotoGroup(currentFamily, targetFamily))
                .willReturn(false);
        given(photoGroupRepository.save(any(PhotoGroup.class)))
                .willReturn(savedPhotoGroup);

        familyService.connectPhotoGroup(
                currentUser,
                createPhotoGroupConnectRequest(10L, "TARGET-1")
        );

        verify(photoGroupRepository).save(any(PhotoGroup.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<PhotoGroupFamily>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(photoGroupFamilyRepository).saveAll(captor.capture());

        List<PhotoGroupFamily> mappings = StreamSupport
                .stream(captor.getValue().spliterator(), false)
                .toList();

        assertThat(mappings).hasSize(2);
        assertThat(mappings)
                .extracting(PhotoGroupFamily::getFamily)
                .containsExactlyInAnyOrder(currentFamily, targetFamily);
        assertThat(mappings)
                .extracting(PhotoGroupFamily::getPhotoGroup)
                .containsOnly(savedPhotoGroup);
    }

    @Test
    void connectPhotoGroupRejectsNonPrimaryMember() {
        Family currentFamily = Family.builder()
                .familyId(1L)
                .build();
        User currentUser = createUser(1L, "부 담당자");
        Senior currentSenior = Senior.builder()
                .seniorId(10L)
                .family(currentFamily)
                .build();
        FamilyMember subMember = createMember(
                1L,
                currentUser,
                currentFamily,
                ManagerType.SUB
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(currentSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, currentFamily))
                .willReturn(true);
        given(familyMemberRepository.findByUserAndFamilyForUpdate(currentUser, currentFamily))
                .willReturn(Optional.of(subMember));

        assertThatThrownBy(() -> familyService.connectPhotoGroup(
                currentUser,
                createPhotoGroupConnectRequest(10L, "TARGET-1")
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PHOTO_GROUP_CONNECTION_FORBIDDEN);

        verify(photoGroupRepository, never()).save(any(PhotoGroup.class));
    }

    @Test
    void connectPhotoGroupRejectsCurrentFamilyCode() {
        Family currentFamily = Family.builder()
                .familyId(1L)
                .seniorCode("CURRENT1")
                .build();
        User currentUser = createUser(1L, "주 담당자");
        Senior currentSenior = Senior.builder()
                .seniorId(10L)
                .family(currentFamily)
                .build();
        FamilyMember primaryMember = createMember(
                1L,
                currentUser,
                currentFamily,
                ManagerType.PRIMARY
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(currentSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, currentFamily))
                .willReturn(true);
        given(familyMemberRepository.findByUserAndFamilyForUpdate(currentUser, currentFamily))
                .willReturn(Optional.of(primaryMember));
        given(familyRepository.findBySeniorCode("CURRENT1"))
                .willReturn(Optional.of(currentFamily));
        given(seniorRepository.findFirstByFamilyOrderBySeniorIdAsc(currentFamily))
                .willReturn(Optional.of(currentSenior));

        assertThatThrownBy(() -> familyService.connectPhotoGroup(
                currentUser,
                createPhotoGroupConnectRequest(10L, "CURRENT1")
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PHOTO_GROUP_SELF_CONNECTION_NOT_ALLOWED);

        verify(photoGroupRepository, never()).save(any(PhotoGroup.class));
    }

    @Test
    void connectPhotoGroupRejectsAlreadyConnectedFamily() {
        Family currentFamily = Family.builder()
                .familyId(1L)
                .build();
        Family targetFamily = Family.builder()
                .familyId(2L)
                .seniorCode("TARGET-1")
                .build();
        User currentUser = createUser(1L, "주 담당자");
        Senior currentSenior = Senior.builder()
                .seniorId(10L)
                .family(currentFamily)
                .build();
        Senior targetSenior = Senior.builder()
                .seniorId(20L)
                .family(targetFamily)
                .build();
        FamilyMember primaryMember = createMember(
                1L,
                currentUser,
                currentFamily,
                ManagerType.PRIMARY
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(currentSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, currentFamily))
                .willReturn(true);
        given(familyMemberRepository.findByUserAndFamilyForUpdate(currentUser, currentFamily))
                .willReturn(Optional.of(primaryMember));
        given(familyRepository.findBySeniorCode("TARGET-1"))
                .willReturn(Optional.of(targetFamily));
        given(seniorRepository.findFirstByFamilyOrderBySeniorIdAsc(targetFamily))
                .willReturn(Optional.of(targetSenior));
        given(photoGroupFamilyRepository.existsSharedPhotoGroup(currentFamily, targetFamily))
                .willReturn(true);

        assertThatThrownBy(() -> familyService.connectPhotoGroup(
                currentUser,
                createPhotoGroupConnectRequest(10L, "TARGET-1")
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PHOTO_GROUP_ALREADY_CONNECTED);

        verify(photoGroupRepository, never()).save(any(PhotoGroup.class));
    }

    @Test
    void disconnectPhotoGroupDeactivatesGroupAndKeepsFamilyLinks() {
        Family currentFamily = Family.builder()
                .familyId(1L)
                .build();
        Family targetFamily = Family.builder()
                .familyId(2L)
                .build();
        User currentUser = createUser(1L, "주 담당자");
        Senior currentSenior = Senior.builder()
                .seniorId(10L)
                .family(currentFamily)
                .build();
        FamilyMember primaryMember = createMember(
                1L,
                currentUser,
                currentFamily,
                ManagerType.PRIMARY
        );
        PhotoGroup photoGroup = PhotoGroup.builder()
                .id(100L)
                .name("Shared Family 1-2")
                .build();
        List<PhotoGroupFamily> groupLinks = List.of(
                PhotoGroupFamily.builder()
                        .family(currentFamily)
                        .photoGroup(photoGroup)
                        .build(),
                PhotoGroupFamily.builder()
                        .family(targetFamily)
                        .photoGroup(photoGroup)
                        .build()
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(currentSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, currentFamily))
                .willReturn(true);
        given(familyMemberRepository.findByUserAndFamilyForUpdate(currentUser, currentFamily))
                .willReturn(Optional.of(primaryMember));
        given(photoGroupFamilyRepository.findAllSharedLinks(currentFamily, 100L))
                .willReturn(groupLinks);

        familyService.disconnectPhotoGroup(currentUser, 10L, 100L);

        assertThat(photoGroup.isActive()).isFalse();
        assertThat(photoGroup.getDisconnectedAt()).isNotNull();
        verify(photoGroupFamilyRepository, never()).deleteAllInBatch(any());
    }

    @Test
    void disconnectPhotoGroupRejectsNonPrimaryMember() {
        Family currentFamily = Family.builder()
                .familyId(1L)
                .build();
        User currentUser = createUser(1L, "부 담당자");
        Senior currentSenior = Senior.builder()
                .seniorId(10L)
                .family(currentFamily)
                .build();
        FamilyMember subMember = createMember(
                1L,
                currentUser,
                currentFamily,
                ManagerType.SUB
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(currentSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, currentFamily))
                .willReturn(true);
        given(familyMemberRepository.findByUserAndFamilyForUpdate(currentUser, currentFamily))
                .willReturn(Optional.of(subMember));

        assertThatThrownBy(() ->
                familyService.disconnectPhotoGroup(currentUser, 10L, 100L)
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PHOTO_GROUP_CONNECTION_FORBIDDEN);

        verify(photoGroupFamilyRepository, never())
                .findAllSharedLinks(currentFamily, 100L);
        verify(photoGroupFamilyRepository, never()).deleteAllInBatch(any());
    }

    @Test
    void disconnectPhotoGroupRejectsUnknownConnection() {
        Family currentFamily = Family.builder()
                .familyId(1L)
                .build();
        User currentUser = createUser(1L, "주 담당자");
        Senior currentSenior = Senior.builder()
                .seniorId(10L)
                .family(currentFamily)
                .build();
        FamilyMember primaryMember = createMember(
                1L,
                currentUser,
                currentFamily,
                ManagerType.PRIMARY
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(currentSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, currentFamily))
                .willReturn(true);
        given(familyMemberRepository.findByUserAndFamilyForUpdate(currentUser, currentFamily))
                .willReturn(Optional.of(primaryMember));
        given(photoGroupFamilyRepository.findAllSharedLinks(currentFamily, 100L))
                .willReturn(List.of());

        assertThatThrownBy(() ->
                familyService.disconnectPhotoGroup(currentUser, 10L, 100L)
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PHOTO_GROUP_CONNECTION_NOT_FOUND);

        verify(photoGroupFamilyRepository, never()).deleteAllInBatch(any());
    }

    @Test
    void getConnectedSeniorsReturnsDirectConnectionWithPhotoGroupId() {
        Family currentFamily = Family.builder()
                .familyId(1L)
                .build();
        Family targetFamily = Family.builder()
                .familyId(2L)
                .build();
        User currentUser = createUser(1L, "현재 사용자");
        Senior currentSenior = Senior.builder()
                .seniorId(10L)
                .family(currentFamily)
                .build();
        Senior targetSenior = Senior.builder()
                .seniorId(20L)
                .name("연결 시니어")
                .relation(SeniorRelation.FATHER)
                .family(targetFamily)
                .build();
        ReflectionTestUtils.setField(targetFamily, "senior", targetSenior);

        LocalDateTime connectedAt = LocalDateTime.of(
                2026,
                9,
                17,
                10,
                0
        );
        PhotoGroup sharedGroup = PhotoGroup.builder()
                .id(100L)
                .name("Shared Family 1-2")
                .build();
        ReflectionTestUtils.setField(sharedGroup, "createdAt", connectedAt);

        PhotoGroupFamily connectedLink = PhotoGroupFamily.builder()
                .id(200L)
                .family(targetFamily)
                .photoGroup(sharedGroup)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(currentSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, currentFamily))
                .willReturn(true);
        given(photoGroupFamilyRepository.findConnectedFamilyLinks(currentFamily))
                .willReturn(List.of(connectedLink));

        var result = familyService.getConnectedSeniors(currentUser, 10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).photoGroupId()).isEqualTo(100L);
        assertThat(result.get(0).seniorId()).isEqualTo(20L);
        assertThat(result.get(0).name()).isEqualTo("연결 시니어");
        assertThat(result.get(0).relation()).isEqualTo(SeniorRelation.FATHER);
        assertThat(result.get(0).connectedAt()).isEqualTo(connectedAt);
    }

    @Test
    void getConnectedSeniorsRejectsUserOutsideSelectedFamily() {
        Family selectedFamily = Family.builder()
                .familyId(1L)
                .build();
        User currentUser = createUser(1L, "다른 가족 사용자");
        Senior selectedSenior = Senior.builder()
                .seniorId(10L)
                .family(selectedFamily)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, selectedFamily))
                .willReturn(false);

        assertThatThrownBy(() ->
                familyService.getConnectedSeniors(currentUser, 10L)
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED);

        verify(photoGroupFamilyRepository, never())
                .findConnectedFamilyLinks(selectedFamily);
    }

    @Test
    void getFamilyMembersRejectsUserOutsideSelectedSeniorFamily() {
        Family otherFamily = Family.builder()
                .familyId(2L)
                .build();
        User currentUser = createUser(1L, "현재 사용자");
        Senior selectedSenior = Senior.builder()
                .seniorId(20L)
                .family(otherFamily)
                .build();

        given(seniorRepository.findById(20L))
                .willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, otherFamily))
                .willReturn(false);

        assertThatThrownBy(() ->
                familyService.getFamilyMembers(currentUser, 20L)
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED);

        verify(familyMemberRepository, never())
                .findAllByFamilyOrderByIdAsc(otherFamily);
    }

    @Test
    void getSeniorCodeReturnsSelectedSeniorFamilyCodeAndMemberCount() {
        Family firstFamily = Family.builder()
                .familyId(1L)
                .seniorCode("FIRST-001")
                .build();
        Family selectedFamily = Family.builder()
                .familyId(2L)
                .seniorCode("SELECTED")
                .build();
        User currentUser = createUser(1L, "현재 사용자");
        currentUser.updateFamily(firstFamily);
        Senior selectedSenior = Senior.builder()
                .seniorId(20L)
                .family(selectedFamily)
                .build();

        given(userRepository.findById(1L))
                .willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(20L))
                .willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, selectedFamily))
                .willReturn(true);
        given(familyMemberRepository.countByFamily(selectedFamily))
                .willReturn(3L);

        var response = familyService.getSeniorCode(currentUser, 20L);

        assertThat(response.getSeniorCode()).isEqualTo("SELECTED");
        assertThat(response.getFamilyMemberCount()).isEqualTo(3L);
        verify(familyMemberRepository).countByFamily(selectedFamily);
        verify(familyMemberRepository, never()).countByFamily(firstFamily);
    }

    @Test
    void updatePrimaryManagerChangesMembersOfSelectedSeniorFamily() {
        Family firstFamily = Family.builder()
                .familyId(1L)
                .build();
        Family selectedFamily = Family.builder()
                .familyId(2L)
                .build();
        User currentUser = createUser(1L, "기존 주 담당자");
        currentUser.updateFamily(firstFamily);
        User targetUser = createUser(2L, "새 주 담당자");
        Senior selectedSenior = Senior.builder()
                .seniorId(20L)
                .family(selectedFamily)
                .build();
        FamilyMember currentMember = createMember(
                1L,
                currentUser,
                selectedFamily,
                ManagerType.PRIMARY
        );
        FamilyMember targetMember = createMember(
                2L,
                targetUser,
                selectedFamily,
                ManagerType.SUB
        );

        given(userRepository.findByIdForUpdate(1L))
                .willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(20L))
                .willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, selectedFamily))
                .willReturn(true);
        given(familyMemberRepository.findByUserAndFamilyForUpdate(currentUser, selectedFamily))
                .willReturn(Optional.of(currentMember));
        given(userRepository.findByIdForUpdate(2L))
                .willReturn(Optional.of(targetUser));
        given(familyMemberRepository.findByUserAndFamilyForUpdate(targetUser, selectedFamily))
                .willReturn(Optional.of(targetMember));

        var response = familyService.updatePrimaryManager(
                currentUser,
                20L,
                createPrimaryManagerUpdateRequest(2L)
        );

        assertThat(currentMember.getManagerType()).isEqualTo(ManagerType.SUB);
        assertThat(targetMember.getManagerType()).isEqualTo(ManagerType.PRIMARY);
        assertThat(response.getUsersId()).isEqualTo(2L);
        assertThat(response.getManagerType()).isEqualTo(ManagerType.PRIMARY);
        verify(familyMemberRepository, never())
                .findByUserAndFamilyForUpdate(currentUser, firstFamily);
    }

    private FamilyJoinRequest createJoinRequest(String seniorCode) {
        FamilyJoinRequest request = new FamilyJoinRequest();
        ReflectionTestUtils.setField(request, "seniorCode", seniorCode);
        return request;
    }

    private FamilyPrimaryManagerUpdateRequest createPrimaryManagerUpdateRequest(
            Long targetUserId
    ) {
        FamilyPrimaryManagerUpdateRequest request =
                new FamilyPrimaryManagerUpdateRequest();
        ReflectionTestUtils.setField(request, "targetUserId", targetUserId);
        return request;
    }

    private PhotoGroupConnectRequest createPhotoGroupConnectRequest(
            Long seniorId,
            String seniorCode
    ) {
        PhotoGroupConnectRequest request = new PhotoGroupConnectRequest();
        ReflectionTestUtils.setField(request, "seniorId", seniorId);
        ReflectionTestUtils.setField(request, "seniorCode", seniorCode);
        return request;
    }

    private User createUser(Long userId, String name) {
        return User.builder()
                .usersId(userId)
                .name(name)
                .role(Role.CHILD)
                .build();
    }

    private FamilyMember createMember(
            Long memberId,
            User user,
            Family family,
            ManagerType managerType
    ) {
        return FamilyMember.builder()
                .id(memberId)
                .user(user)
                .family(family)
                .managerType(managerType)
                .build();
    }

    @Test
    void removeFamilyMemberRejectsSeniorParentUser() {
        Family family = Family.builder()
                .familyId(1L)
                .build();
        User currentUser = createUser(1L, "주 담당자");
        User seniorParentUser = User.builder()
                .usersId(2L)
                .name("시니어 본인")
                .role(Role.PARENT)
                .build();
        Senior senior = Senior.builder()
                .seniorId(10L)
                .family(family)
                .parentUser(seniorParentUser)
                .build();
        FamilyMember primaryMember = createMember(
                1L,
                currentUser,
                family,
                ManagerType.PRIMARY
        );

        given(userRepository.findByIdForUpdate(1L))
                .willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(10L))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, family))
                .willReturn(true);
        given(familyMemberRepository.findByUserAndFamilyForUpdate(currentUser, family))
                .willReturn(Optional.of(primaryMember));
        given(userRepository.findByIdForUpdate(2L))
                .willReturn(Optional.of(seniorParentUser));
        given(familyMemberRepository.existsByUserAndFamily(seniorParentUser, family))
                .willReturn(true);

        assertThatThrownBy(() ->
                familyService.removeFamilyMember(currentUser, 10L, 2L)
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.CANNOT_REMOVE_SENIOR_PARENT);

        verify(familyMemberRepository, never())
                .deleteByUserAndFamily(seniorParentUser, family);
    }

    @Test
    void removeFamilyMemberDeletesMemberOnlyFromSelectedSeniorFamily() {
        Family firstFamily = Family.builder()
                .familyId(1L)
                .build();
        Family selectedFamily = Family.builder()
                .familyId(2L)
                .build();

        User currentUser = createUser(1L, "주 담당자");
        currentUser.updateFamily(firstFamily);
        User targetUser = createUser(2L, "삭제 대상");

        Senior selectedSenior = Senior.builder()
                .seniorId(20L)
                .family(selectedFamily)
                .build();

        FamilyMember currentMember = createMember(
                1L,
                currentUser,
                selectedFamily,
                ManagerType.PRIMARY
        );

        given(userRepository.findByIdForUpdate(1L))
                .willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(20L))
                .willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, selectedFamily))
                .willReturn(true);
        given(familyMemberRepository.findByUserAndFamilyForUpdate(currentUser, selectedFamily))
                .willReturn(Optional.of(currentMember));
        given(userRepository.findByIdForUpdate(2L))
                .willReturn(Optional.of(targetUser));
        given(familyMemberRepository.existsByUserAndFamily(targetUser, selectedFamily))
                .willReturn(true);

        familyService.removeFamilyMember(currentUser, 20L, 2L);

        verify(familyMemberRepository)
                .deleteByUserAndFamily(targetUser, selectedFamily);
        verify(familyMemberRepository, never())
                .deleteByUserAndFamily(targetUser, firstFamily);
    }
}
