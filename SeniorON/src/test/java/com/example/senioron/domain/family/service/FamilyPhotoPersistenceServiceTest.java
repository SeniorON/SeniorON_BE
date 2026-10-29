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
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.event.FamilyPhotoSharedEvent;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
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

    private static final List<ManagerType> MANAGER_TYPES = List.of(ManagerType.PRIMARY, ManagerType.SUB);

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
    private final PhotoGroupFamilyRepository photoGroupFamilyRepository = org.mockito.Mockito.mock(PhotoGroupFamilyRepository.class);
    private final FamilyPhotoSeniorRepository familyPhotoSeniorRepository =
            org.mockito.Mockito.mock(FamilyPhotoSeniorRepository.class);
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
                photoGroupFamilyRepository,
                familyPhotoSeniorRepository
        );
    }

    @ParameterizedTest
    @EnumSource(value = ManagerType.class, names = {"PRIMARY", "SUB"})
    void createWithSeniorsSavesShareTargetsAndDefaultGroupMappings(ManagerType managerType) {
        User user = User.builder()
                .usersId(2L)
                .name("업로더")
                .role(Role.CHILD)
                .build();
        ShareTarget first = shareTarget(user, 20L, 3L);
        ShareTarget second = shareTarget(user, 21L, 4L);

        stubUploadAccess(user, List.of(first, second), managerType);
        given(familyPhotoRepository.saveAndFlush(any(FamilyPhoto.class)))
                .willAnswer(invocation -> {
                    FamilyPhoto photo = invocation.getArgument(0);
                    ReflectionTestUtils.setField(photo, "familyPhotoId", 10L);
                    return photo;
                });

        FamilyPhoto savedPhoto = persistenceService.create(
                2L,
                "family-photos/1/multi-senior-photo.jpg",
                "family-photos/thumbnails/multi-senior-photo.jpg",
                "multi-senior-idempotency-key",
                "여러 시니어 공유",
                1L,
                List.of(21L, 20L, 21L)
        );

        assertThat(savedPhoto.getPhotoGroup()).isSameAs(first.group());
        var accessOrder = inOrder(userRepository, familyMemberRepository, familyPhotoRepository);
        accessOrder.verify(userRepository).findByIdForUpdate(2L);
        accessOrder.verify(familyMemberRepository).findByUserAndFamily(user, family);
        accessOrder.verify(familyMemberRepository).findPhotoShareTargetMembers(user, MANAGER_TYPES);
        accessOrder.verify(familyPhotoRepository).saveAndFlush(any(FamilyPhoto.class));
        assertThat(savedPhoto.getThumbnailKey())
                .isEqualTo("family-photos/thumbnails/multi-senior-photo.jpg");
        verify(familyPhotoSeniorRepository).saveAllAndFlush(
                argThat(targets -> {
                    List<FamilyPhotoSenior> savedTargets =
                            StreamSupport.stream(targets.spliterator(), false).toList();

                    return savedTargets.size() == 2
                            && savedTargets.stream().allMatch(target ->
                            target.getFamilyPhoto() == savedPhoto)
                            && savedTargets.stream()
                            .map(FamilyPhotoSenior::getSenior)
                            .toList()
                            .equals(List.of(first.senior(), second.senior()));
                })
        );
        verify(familyPhotoGroupRepository).saveAllAndFlush(
                argThat(mappings -> {
                    List<FamilyPhotoGroup> savedMappings =
                            StreamSupport.stream(mappings.spliterator(), false).toList();

                    return savedMappings.size() == 2
                            && savedMappings.stream().allMatch(mapping ->
                            mapping.getFamilyPhoto() == savedPhoto)
                            && savedMappings.stream()
                            .map(FamilyPhotoGroup::getPhotoGroup)
                            .toList()
                            .equals(List.of(first.group(), second.group()));
                })
        );
        var ordered = inOrder(familyPhotoGroupRepository, familyPhotoSeniorRepository, eventPublisher);
        ordered.verify(familyPhotoGroupRepository).saveAllAndFlush(any());
        ordered.verify(familyPhotoSeniorRepository).saveAllAndFlush(any());
        ordered.verify(eventPublisher).publishEvent(new FamilyPhotoSharedEvent(10L));
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void doesNotPublishWhenSavingShareTargetsFails() {
        User user = User.builder().usersId(2L).name("업로더").role(Role.CHILD).build();
        ShareTarget target = shareTarget(user, 20L, 3L);
        stubUploadAccess(user, List.of(target), ManagerType.PRIMARY);
        given(familyPhotoRepository.saveAndFlush(any(FamilyPhoto.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataIntegrityViolationException("share target failed"))
                .when(familyPhotoSeniorRepository).saveAllAndFlush(any());

        assertThatThrownBy(() -> persistenceService.create(
                2L, "photo.jpg", null, "key", "설명", 1L, List.of(20L)
        )).isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"emptyTargets", "removedMember", "unmanagedTarget", "parent", "withdrawn",
            "noManagerRole"})
    void rejectsRevokedAccessBeforeSavingOrPublishing(String reason) {
        User user = User.builder().usersId(2L).name("업로더").role(Role.CHILD).build();
        ShareTarget target = shareTarget(user, 20L, 3L);
        ManagerType managerType = reason.equals("noManagerRole") ? ManagerType.NONE : ManagerType.PRIMARY;
        stubUploadAccess(user, List.of(target), managerType);
        List<Long> seniorIds = reason.equals("emptyTargets") ? List.of() : List.of(20L);
        ErrorCode expected = ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN;
        switch (reason) {
            case "removedMember" -> {
                given(familyMemberRepository.findByUserAndFamily(user, family)).willReturn(Optional.empty());
                expected = ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED;
            }
            case "unmanagedTarget" -> given(familyMemberRepository
                    .findPhotoShareTargetMembers(user, MANAGER_TYPES))
                    .willReturn(List.of());
            case "parent" -> { user.updateRole(Role.PARENT); expected = ErrorCode.FORBIDDEN; }
            case "withdrawn" -> {
                ReflectionTestUtils.setField(user, "status", UserStatus.WITHDRAWN);
                expected = ErrorCode.FORBIDDEN;
            }
        }
        ErrorCode expectedCode = expected;
        assertThatThrownBy(() -> persistenceService.create(
                2L, "photo.jpg", null, "key", "설명", 1L, seniorIds
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(expectedCode));
        verifyNoInteractions(familyPhotoRepository, familyPhotoGroupRepository, familyPhotoSeniorRepository,
                eventPublisher);
    }

    private ShareTarget shareTarget(User user, Long seniorId, Long groupId) {
        Family targetFamily = Family.builder().familyId(seniorId + 100).build();
        Senior senior = Senior.builder().seniorId(seniorId).family(targetFamily).build();
        ReflectionTestUtils.setField(targetFamily, "senior", senior);
        PhotoGroup group = PhotoGroup.builder().id(groupId).name("기본 그룹 " + groupId).build();
        FamilyMember member = FamilyMember.builder()
                .user(user).family(targetFamily).managerType(ManagerType.SUB).build();
        return new ShareTarget(senior, group, member);
    }

    private void stubUploadAccess(User user, List<ShareTarget> targets, ManagerType managerType) {
        given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(user));
        given(familyRepository.findById(1L)).willReturn(Optional.of(family));
        given(familyMemberRepository.findByUserAndFamily(user, family)).willReturn(Optional.of(
                FamilyMember.builder().user(user).family(family).managerType(managerType).build()));
        given(familyMemberRepository.findPhotoShareTargetMembers(user, MANAGER_TYPES))
                .willReturn(targets.stream().map(ShareTarget::member).toList());
        targets.forEach(target -> given(photoGroupFamilyRepository
                .findFirstByFamilyOrderByIdAsc(target.senior().getFamily()))
                .willReturn(Optional.of(PhotoGroupFamily.builder()
                        .family(target.senior().getFamily()).photoGroup(target.group()).build())));
    }

    private record ShareTarget(Senior senior, PhotoGroup group, FamilyMember member) {}
}
