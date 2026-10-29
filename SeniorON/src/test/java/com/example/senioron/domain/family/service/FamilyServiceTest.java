package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.dto.request.FamilyPrimaryManagerUpdateRequest;
import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoViewRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupRepository;
import com.example.senioron.domain.family.support.FamilyPhotoResponseMapper;
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
import java.util.Set;
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
                seniorRepository,
                new FamilyPhotoResponseMapper(s3Service)
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
        given(familyMemberRepository.findAllByFamilyOrderByIdAsc(family))
                .willReturn(List.of());
        given(familyPhotoRepository.findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
                family,
                PageRequest.of(0, 4)
        )).willReturn(List.of(photo));
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
    void getFamilyHomeReturnsThumbnailUrlWithOriginalFallback() {
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
        FamilyPhoto withThumbnail = FamilyPhoto.builder()
                .familyPhotoId(31L)
                .photoGroup(defaultPhotoGroup)
                .user(uploader)
                .imageKey("photo-31.jpg")
                .thumbnailKey("thumb-31.jpg")
                .build();
        FamilyPhoto withoutThumbnail = FamilyPhoto.builder()
                .familyPhotoId(32L)
                .photoGroup(defaultPhotoGroup)
                .user(uploader)
                .imageKey("photo-32.jpg")
                .build();
        FamilyPhoto blankThumbnail = FamilyPhoto.builder()
                .familyPhotoId(33L)
                .photoGroup(defaultPhotoGroup)
                .user(uploader)
                .imageKey("photo-33.jpg")
                .thumbnailKey(" ")
                .build();
        List<FamilyPhoto> photos =
                List.of(withThumbnail, withoutThumbnail, blankThumbnail);
        photos.forEach(photo -> ReflectionTestUtils.setField(
                photo,
                "createdAt",
                LocalDateTime.now()
        ));

        given(userRepository.findById(1L))
                .willReturn(Optional.of(parent));
        given(seniorRepository.findById(10L))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(parent, family))
                .willReturn(true);
        given(familyMemberRepository.findAllByFamilyOrderByIdAsc(family))
                .willReturn(List.of());
        given(familyPhotoRepository.findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
                family,
                PageRequest.of(0, 4)
        )).willReturn(photos);
        given(familyPhotoViewRepository.findViewedFamilyPhotoIds(
                1L,
                List.of(31L, 32L, 33L)
        )).willReturn(List.of(32L));
        given(familyPhotoPermissionService.findDeletablePhotoIds(photos, parent))
                .willReturn(Set.of(31L));
        given(s3Service.getFileUrl("photo-31.jpg"))
                .willReturn("https://cdn/photo-31.jpg");
        given(s3Service.getFileUrl("thumb-31.jpg"))
                .willReturn("https://cdn/thumb-31.jpg");
        given(s3Service.getFileUrl("photo-32.jpg"))
                .willReturn("https://cdn/photo-32.jpg");
        given(s3Service.getFileUrl("photo-33.jpg"))
                .willReturn("https://cdn/photo-33.jpg");

        var response = familyService.getFamilyHome(parent, 10L);

        assertThat(response.getRecentPhotos())
                .extracting(
                        FamilyPhotoItemResponse::getFamilyPhotoId,
                        FamilyPhotoItemResponse::getImageUrl,
                        FamilyPhotoItemResponse::getThumbnailUrl,
                        FamilyPhotoItemResponse::isCanDelete,
                        FamilyPhotoItemResponse::isNewPhoto
                )
                .containsExactly(
                        tuple(31L, "https://cdn/photo-31.jpg",
                                "https://cdn/thumb-31.jpg", true, true),
                        tuple(32L, "https://cdn/photo-32.jpg",
                                "https://cdn/photo-32.jpg", false, false),
                        tuple(33L, "https://cdn/photo-33.jpg",
                                "https://cdn/photo-33.jpg", false, true)
                );
        verify(familyPhotoPermissionService, never())
                .canDelete(any(), any());
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
        currentUser.updateManagerType(ManagerType.SUB);
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
    void getSeniorCodeAllowsSelectedFamilySubManager() {
        Family selectedFamily = Family.builder()
                .familyId(2L)
                .seniorCode("SELECTED")
                .build();
        User currentUser = createUser(1L, "보조 담당자");
        currentUser.updateManagerType(ManagerType.SUB);
        Senior selectedSenior = Senior.builder()
                .seniorId(20L)
                .family(selectedFamily)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(20L)).willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, selectedFamily))
                .willReturn(true);
        given(familyMemberRepository.countByFamily(selectedFamily))
                .willReturn(3L);

        var response = familyService.getSeniorCode(currentUser, 20L);

        assertThat(response.getSeniorCode()).isEqualTo("SELECTED");
        assertThat(response.getFamilyMemberCount()).isEqualTo(3L);
    }

    @Test
    void getSeniorCodeAllowsSelectedFamilySenior() {
        Family selectedFamily = Family.builder()
                .familyId(2L)
                .seniorCode("SELECTED")
                .build();
        User seniorUser = User.builder()
                .usersId(1L)
                .name("시니어")
                .role(Role.PARENT)
                .build();
        Senior selectedSenior = Senior.builder()
                .seniorId(20L)
                .family(selectedFamily)
                .parentUser(seniorUser)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(seniorUser));
        given(seniorRepository.findById(20L)).willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(seniorUser, selectedFamily))
                .willReturn(true);
        given(familyMemberRepository.countByFamily(selectedFamily))
                .willReturn(3L);

        var response = familyService.getSeniorCode(seniorUser, 20L);

        assertThat(response.getSeniorCode()).isEqualTo("SELECTED");
        assertThat(response.getFamilyMemberCount()).isEqualTo(3L);
    }

    @Test
    void getSeniorCodeRejectsUserOutsideSelectedFamily() {
        Family selectedFamily = Family.builder().familyId(2L).build();
        User currentUser = createUser(1L, "다른 가족 사용자");
        Senior selectedSenior = Senior.builder()
                .seniorId(20L)
                .family(selectedFamily)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(currentUser));
        given(seniorRepository.findById(20L)).willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(currentUser, selectedFamily))
                .willReturn(false);

        assertThatThrownBy(() -> familyService.getSeniorCode(currentUser, 20L))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED);

        verify(familyMemberRepository, never()).countByFamily(any());
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
