package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.event.FamilyPhotoSharedEvent;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.PhotoGroupRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class FamilyPhotoPersistenceServiceTest {

    private final FamilyPhotoRepository familyPhotoRepository =
            org.mockito.Mockito.mock(FamilyPhotoRepository.class);
    private final UserRepository userRepository =
            org.mockito.Mockito.mock(UserRepository.class);
    private final FamilyPhotoGroupRepository familyPhotoGroupRepository =
            org.mockito.Mockito.mock(FamilyPhotoGroupRepository.class);
    private final ApplicationEventPublisher eventPublisher =
            org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final FamilyRepository familyRepository = org.mockito.Mockito.mock(FamilyRepository.class);
    private final FamilyMemberRepository familyMemberRepository = org.mockito.Mockito.mock(FamilyMemberRepository.class);
    private final PhotoGroupRepository photoGroupRepository = org.mockito.Mockito.mock(PhotoGroupRepository.class);
    private final PhotoGroupFamilyRepository photoGroupFamilyRepository = org.mockito.Mockito.mock(PhotoGroupFamilyRepository.class);
    private final Family family = Family.builder().familyId(1L).build();

    private FamilyPhotoPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new FamilyPhotoPersistenceService(
                familyPhotoRepository,
                userRepository,
                familyPhotoGroupRepository,
                eventPublisher,
                familyRepository,
                familyMemberRepository,
                photoGroupRepository,
                photoGroupFamilyRepository
        );
    }

    @ParameterizedTest
    @EnumSource(value = ManagerType.class, names = {"PRIMARY", "SUB"})
    void createWithPhotoGroupsSavesAllMappings(ManagerType managerType) {
        User user = User.builder()
                .usersId(2L)
                .name("업로더")
                .role(Role.CHILD)
                .build();
        PhotoGroup firstGroup = PhotoGroup.builder()
                .id(3L)
                .name("첫 번째 그룹")
                .build();
        PhotoGroup secondGroup = PhotoGroup.builder()
                .id(4L)
                .name("두 번째 그룹")
                .build();

        stubUploadAccess(user, List.of(firstGroup, secondGroup), managerType);
        given(familyPhotoRepository.saveAndFlush(any(FamilyPhoto.class)))
                .willAnswer(invocation -> {
                    FamilyPhoto photo = invocation.getArgument(0);
                    ReflectionTestUtils.setField(photo, "familyPhotoId", 10L);
                    return photo;
                });

        FamilyPhoto savedPhoto = persistenceService.create(
                2L,
                "family-photos/1/multi-group-photo.jpg",
                "family-photos/thumbnails/multi-group-photo.jpg",
                "multi-group-idempotency-key",
                "여러 그룹 공유",
                1L,
                List.of(4L, 3L, 4L)
        );

        assertThat(savedPhoto.getPhotoGroup()).isSameAs(firstGroup);
        var accessOrder = inOrder(userRepository, photoGroupRepository, familyMemberRepository,
                familyPhotoRepository);
        accessOrder.verify(userRepository).findByIdForUpdate(2L);
        accessOrder.verify(photoGroupRepository).findAllByIdsForUpdate(List.of(3L, 4L));
        accessOrder.verify(familyMemberRepository).findByUserAndFamily(user, family);
        accessOrder.verify(familyPhotoRepository).saveAndFlush(any(FamilyPhoto.class));
        assertThat(savedPhoto.getThumbnailKey())
                .isEqualTo("family-photos/thumbnails/multi-group-photo.jpg");
        verify(familyPhotoGroupRepository).saveAllAndFlush(
                argThat(mappings -> {
                    List<FamilyPhotoGroup> savedMappings =
                            StreamSupport.stream(
                                    mappings.spliterator(),
                                    false
                            ).toList();

                    return savedMappings.size() == 2
                            && savedMappings.stream().allMatch(mapping ->
                            mapping.getFamilyPhoto() == savedPhoto
                    )
                            && savedMappings.stream()
                            .map(FamilyPhotoGroup::getPhotoGroup)
                            .toList()
                            .equals(List.of(firstGroup, secondGroup));
                })
        );
        var ordered = inOrder(familyPhotoGroupRepository, eventPublisher);
        ordered.verify(familyPhotoGroupRepository).saveAllAndFlush(any());
        ordered.verify(eventPublisher).publishEvent(new FamilyPhotoSharedEvent(10L));
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void doesNotPublishWhenSavingSharedGroupMappingsFails() {
        User user = User.builder().usersId(2L).name("업로더").role(Role.CHILD).build();
        PhotoGroup group = PhotoGroup.builder().id(3L).name("가족").build();
        stubUploadAccess(user, List.of(group), ManagerType.PRIMARY);
        given(familyPhotoRepository.saveAndFlush(any(FamilyPhoto.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataIntegrityViolationException("mapping failed"))
                .when(familyPhotoGroupRepository).saveAllAndFlush(any());

        assertThatThrownBy(() -> persistenceService.create(
                2L, "photo.jpg", null, "key", "설명", 1L, List.of(3L)
        )).isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactiveGroup", "missingGroup", "removedMember", "unmanagedTarget",
            "parent", "withdrawn", "noManagerRole"})
    void rejectsRevokedAccessBeforeSavingOrPublishing(String reason) {
        User user = User.builder().usersId(2L).name("업로더").role(Role.CHILD).build();
        PhotoGroup group = PhotoGroup.builder().id(3L).name("공유 그룹").build();
        ManagerType managerType = reason.equals("noManagerRole") ? ManagerType.NONE
                : reason.equals("unmanagedTarget")
                ? ManagerType.SUB : ManagerType.PRIMARY;
        stubUploadAccess(user, List.of(group), managerType);
        ErrorCode expected = ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN;
        switch (reason) {
            case "inactiveGroup" -> group.disconnect();
            case "missingGroup" -> given(photoGroupRepository.findAllByIdsForUpdate(List.of(3L)))
                    .willReturn(List.of());
            case "removedMember" -> {
                given(familyMemberRepository.findByUserAndFamily(user, family)).willReturn(Optional.empty());
                expected = ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED;
            }
            case "unmanagedTarget" -> given(photoGroupFamilyRepository
                    .findManagedUploadGroupLinks(family, user, List.of(3L))).willReturn(List.of());
            case "parent" -> { user.updateRole(Role.PARENT); expected = ErrorCode.FORBIDDEN; }
            case "withdrawn" -> {
                ReflectionTestUtils.setField(user, "status", UserStatus.WITHDRAWN);
                expected = ErrorCode.FORBIDDEN;
            }
        }
        ErrorCode expectedCode = expected;
        assertThatThrownBy(() -> persistenceService.create(
                2L, "photo.jpg", null, "key", "설명", 1L, List.of(3L)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(expectedCode));
        verifyNoInteractions(familyPhotoRepository, familyPhotoGroupRepository, eventPublisher);
    }

    private void stubUploadAccess(User user, List<PhotoGroup> groups, ManagerType managerType) {
        List<Long> ids = groups.stream().map(PhotoGroup::getId).toList();
        given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(user));
        given(photoGroupRepository.findAllByIdsForUpdate(ids)).willReturn(groups);
        given(familyRepository.findById(1L)).willReturn(Optional.of(family));
        given(familyMemberRepository.findByUserAndFamily(user, family)).willReturn(Optional.of(
                FamilyMember.builder().user(user).family(family).managerType(managerType).build()));
        List<PhotoGroupFamily> links = groups.stream().map(group -> PhotoGroupFamily.builder()
                .family(family).photoGroup(group).build()).toList();
        if (managerType == ManagerType.PRIMARY) {
            given(photoGroupFamilyRepository.findAllByFamilyAndPhotoGroupIds(family, ids)).willReturn(links);
        } else if (managerType == ManagerType.SUB) {
            given(photoGroupFamilyRepository.findManagedUploadGroupLinks(family, user, ids)).willReturn(links);
        }
    }
}
