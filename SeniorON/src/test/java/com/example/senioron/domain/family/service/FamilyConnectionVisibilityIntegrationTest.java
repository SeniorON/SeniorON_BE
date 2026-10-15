package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.dto.request.FamilyPhotoCreateRequest;
import com.example.senioron.domain.family.dto.request.FamilyPhotoUploadCompleteRequest;
import com.example.senioron.domain.family.dto.request.FamilyPrimaryManagerUpdateRequest;
import com.example.senioron.domain.family.dto.request.PhotoGroupConnectRequest;
import com.example.senioron.domain.family.dto.response.ConnectedSeniorResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupRepository;
import com.example.senioron.domain.senior.dto.response.ManagedSeniorResponse;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.service.SeniorService;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import com.example.senioron.global.storage.StoredObjectInfo;
import com.example.senioron.global.storage.ThumbnailService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({FamilyService.class, SeniorService.class, FamilyPhotoService.class})
class FamilyConnectionVisibilityIntegrationTest {

    @Autowired private FamilyService familyService;
    @Autowired private SeniorService seniorService;
    @Autowired private FamilyPhotoService familyPhotoService;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private SeniorRepository seniorRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PhotoGroupRepository photoGroupRepository;
    @Autowired private PhotoGroupFamilyRepository photoGroupFamilyRepository;
    @Autowired private EntityManager entityManager;

    @MockitoBean private S3Service s3Service;
    @MockitoBean private FamilyPhotoPermissionService familyPhotoPermissionService;
    @MockitoBean private DeviceService deviceService;
    @MockitoBean private ThumbnailService thumbnailService;
    @MockitoBean private FamilyPhotoPersistenceService photoPersistenceService;
    @MockitoBean private FamilyPhotoPushTargetService familyPhotoPushTargetService;

    @BeforeEach
    void stubPhotoStorage() {
        given(s3Service.findObjectInfo(anyString()))
                .willReturn(java.util.Optional.of(new StoredObjectInfo(1024L, "image/jpeg")));
        given(s3Service.download(anyString())).willReturn(new byte[]{1});
        given(thumbnailService.create(any())).willReturn(new byte[]{2});
        given(s3Service.uploadThumbnail(any())).willReturn("thumbnail.jpg");
        given(photoPersistenceService.create(anyLong(), anyString(), anyString(), anyString(), any(), anyLong(), anyList()))
                .willAnswer(invocation -> FamilyPhoto.builder()
                        .familyPhotoId(42L)
                        .user(userRepository.findById(invocation.getArgument(0, Long.class)).orElseThrow())
                        .imageKey(invocation.getArgument(1))
                        .build());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsEntireUploadWhenOneRecipientIsNotManaged(boolean multipart) {
        FamilyScenario scenario = saveScenario(true, false);
        List<Long> groupIds = List.of(
                connectedGroupId(scenario, scenario.father()), connectedGroupId(scenario, scenario.grandmother())
        );

        assertUploadForbidden(scenario.assistant(), scenario.mother(), groupIds, multipart);

        verifyNoInteractions(photoPersistenceService, s3Service);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sharedConnectionAndMembershipBothControlRecipientsAndUploads(boolean multipart) {
        FamilyScenario scenario = saveScenario(false, false);
        Long sharedGroupId = connectedGroupId(scenario, scenario.father());
        List<Long> groupIds = List.of(defaultGroupId(scenario.mother()), sharedGroupId);
        long originalGroupCount = photoGroupRepository.count();
        long originalLinkCount = photoGroupFamilyRepository.count();

        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).isEmpty();
        assertUploadForbidden(scenario.assistant(), scenario.mother(), groupIds, multipart);
        verifyNoInteractions(photoPersistenceService, s3Service);

        familyService.joinFamily(scenario.assistant(), joinRequest(scenario.fatherFamily().getSeniorCode()));
        flushAndClear();
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).singleElement().satisfies(connection -> {
            assertThat(connection.seniorId()).isEqualTo(scenario.father().getSeniorId());
            assertThat(connection.photoGroupId()).isEqualTo(sharedGroupId);
        });
        assertThat(upload(scenario.assistant(), scenario.mother(), groupIds, multipart).getFamilyPhotoId())
                .isEqualTo(42L);

        familyService.removeFamilyMember(
                scenario.primary(), scenario.father().getSeniorId(), scenario.assistant().getUsersId()
        );
        flushAndClear();
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).isEmpty();
        assertUploadForbidden(scenario.assistant(), scenario.mother(), groupIds, multipart);

        familyService.joinFamily(scenario.assistant(), joinRequest(scenario.fatherFamily().getSeniorCode()));
        flushAndClear();
        assertThat(upload(scenario.assistant(), scenario.mother(), groupIds, multipart).getFamilyPhotoId())
                .isEqualTo(42L);

        familyService.disconnectPhotoGroup(scenario.primary(), scenario.mother().getSeniorId(), sharedGroupId);
        flushAndClear();
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).isEmpty();
        assertUploadForbidden(scenario.assistant(), scenario.mother(), groupIds, multipart);
        assertThat(photoGroupRepository.count()).isEqualTo(originalGroupCount);
        assertThat(photoGroupFamilyRepository.count()).isEqualTo(originalLinkCount);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> groups = ArgumentCaptor.forClass(List.class);
        verify(photoPersistenceService, times(2)).create(
                anyLong(), anyString(), anyString(), anyString(), any(), anyLong(), groups.capture()
        );
        assertThat(groups.getAllValues()).allSatisfy(value ->
                assertThat(value).containsExactlyElementsOf(groupIds));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void assistantCanUploadToOnlyJoinedSeniorWithoutAnySharingConnections(boolean multipart) {
        FamilyScenario scenario = saveScenario(false, false, false);
        Long motherGroupId = defaultGroupId(scenario.mother());

        assertThat(seniorService.getManagedSeniors(scenario.assistant()))
                .extracting(ManagedSeniorResponse::seniorId).containsExactly(scenario.mother().getSeniorId());
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).isEmpty();
        assertThat(upload(scenario.assistant(), scenario.mother(), List.of(motherGroupId), multipart)
                .getFamilyPhotoId()).isEqualTo(42L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void joiningTwoSeniorsDoesNotCreateSharingConnectionsOrAllowOtherDefaultAlbums(boolean multipart) {
        FamilyScenario scenario = saveScenario(true, false, false);
        assertThat(seniorService.getManagedSeniors(scenario.assistant()))
                .extracting(ManagedSeniorResponse::seniorId)
                .containsExactly(scenario.mother().getSeniorId(), scenario.father().getSeniorId());
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).isEmpty();
        assertUploadForbidden(scenario.assistant(), scenario.mother(),
                List.of(defaultGroupId(scenario.father())), multipart);
        assertThat(photoGroupRepository.count()).isEqualTo(3);
        assertThat(photoGroupFamilyRepository.count()).isEqualTo(3);
        verifyNoInteractions(photoPersistenceService, s3Service);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void primaryCanConnectShareAndDisconnectWithoutJoiningTheTargetFamily(boolean multipart) {
        FamilyScenario scenario = saveScenario(false, false, false);
        familyService.updatePrimaryManager(scenario.primary(), scenario.mother().getSeniorId(),
                primaryManagerRequest(scenario.assistant().getUsersId()));
        PhotoGroupConnectRequest request = new PhotoGroupConnectRequest();
        ReflectionTestUtils.setField(request, "seniorId", scenario.mother().getSeniorId());
        ReflectionTestUtils.setField(request, "seniorCode", scenario.grandmother().getFamily().getSeniorCode());
        familyService.connectPhotoGroup(scenario.assistant(), request);
        flushAndClear();

        assertThat(familyMemberRepository.existsByUserAndFamily(
                scenario.assistant(), scenario.grandmother().getFamily()
        )).isFalse();
        List<ConnectedSeniorResponse> connections = familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId());
        assertThat(connections).extracting(ConnectedSeniorResponse::seniorId)
                .containsExactly(scenario.grandmother().getSeniorId());
        Long groupId = connections.get(0).photoGroupId();
        assertThat(upload(scenario.assistant(), scenario.mother(), List.of(groupId), multipart)
                .getFamilyPhotoId()).isEqualTo(42L);

        familyService.disconnectPhotoGroup(scenario.assistant(), scenario.mother().getSeniorId(), groupId);
        flushAndClear();
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId())).isEmpty();
        assertUploadForbidden(scenario.assistant(), scenario.mother(), List.of(groupId), multipart);
        verify(photoPersistenceService).create(
                anyLong(), anyString(), anyString(), anyString(), any(), anyLong(), anyList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void uploadRejectsUnmanagedSelectedSeniorEvenWhenRecipientIsManaged(boolean multipart) {
        FamilyScenario scenario = saveScenario(true, false, false);

        assertThatThrownBy(() -> upload(
                scenario.assistant(), scenario.grandmother(), List.of(defaultGroupId(scenario.mother())), multipart
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED));
        verifyNoInteractions(photoPersistenceService, s3Service);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void selectedFamilyRoleControlsUploadEvenWhenPrimaryElsewhere(boolean multipart) {
        FamilyScenario scenario = saveScenario(true, true);
        List<Long> groupIds = List.of(connectedGroupId(scenario, scenario.grandmother()));

        assertUploadForbidden(scenario.assistant(), scenario.mother(), groupIds, multipart);
        verifyNoInteractions(photoPersistenceService, s3Service);

        familyService.updatePrimaryManager(
                scenario.primary(), scenario.mother().getSeniorId(),
                primaryManagerRequest(scenario.assistant().getUsersId())
        );
        flushAndClear();
        assertThat(upload(scenario.assistant(), scenario.mother(), groupIds, multipart).getFamilyPhotoId())
                .isEqualTo(42L);

        familyService.updatePrimaryManager(
                scenario.assistant(), scenario.mother().getSeniorId(),
                primaryManagerRequest(scenario.primary().getUsersId())
        );
        flushAndClear();
        assertUploadForbidden(scenario.assistant(), scenario.mother(), groupIds, multipart);
        verify(photoPersistenceService).create(
                anyLong(), anyString(), anyString(), anyString(), any(), anyLong(), anyList()
        );
    }

    private Long connectedGroupId(FamilyScenario scenario, Senior target) {
        return familyService.getConnectedSeniors(scenario.primary(), scenario.mother().getSeniorId())
                .stream().filter(connection -> connection.seniorId().equals(target.getSeniorId()))
                .findFirst().orElseThrow().photoGroupId();
    }

    private Long defaultGroupId(Senior senior) {
        return photoGroupFamilyRepository.findFirstByFamilyOrderByIdAsc(senior.getFamily())
                .orElseThrow().getPhotoGroup().getId();
    }

    private void assertUploadForbidden(User user, Senior selected, List<Long> groupIds, boolean multipart) {
        assertThatThrownBy(() -> upload(user, selected, groupIds, multipart))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN));
    }

    private FamilyPhotoItemResponse upload(User user, Senior selected, List<Long> groupIds, boolean multipart) {
        String imageKey = "family-photos/" + selected.getFamily().getFamilyId()
                + "/" + user.getUsersId() + "/" + UUID.randomUUID() + ".jpg";
        String idempotencyKey = UUID.randomUUID().toString();
        if (multipart) {
            given(s3Service.upload(any(), anyString())).willReturn(imageKey);
            FamilyPhotoCreateRequest request = new FamilyPhotoCreateRequest();
            request.setSeniorId(selected.getSeniorId());
            request.setPhotoGroupIds(groupIds);
            request.setImage(new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[]{1}));
            return familyPhotoService.createPhoto(user, idempotencyKey, request);
        }
        FamilyPhotoUploadCompleteRequest request = new FamilyPhotoUploadCompleteRequest();
        request.setSeniorId(selected.getSeniorId());
        request.setPhotoGroupIds(groupIds);
        request.setImageKey(imageKey);
        return familyPhotoService.completePhotoUpload(user, idempotencyKey, request);
    }

    @Test
    void assistantsSeeOnlyManagedRelativesWhilePrimaryKeepsAllSharedConnections() {
        FamilyScenario scenario = saveScenario(true, false);

        assertThat(familyService.getConnectedSeniors(
                scenario.primary(), scenario.mother().getSeniorId()
        )).extracting(ConnectedSeniorResponse::seniorId)
                .containsExactly(scenario.father().getSeniorId(), scenario.grandmother().getSeniorId());
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).extracting(ConnectedSeniorResponse::seniorId)
                .containsExactly(scenario.father().getSeniorId());
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.father().getSeniorId()
        )).extracting(ConnectedSeniorResponse::seniorId)
                .containsExactly(scenario.mother().getSeniorId());
        assertThat(familyService.getConnectedSeniors(
                scenario.otherAssistant(), scenario.grandmother().getSeniorId()
        )).isEmpty();

        // This endpoint returns other seniors; the selected senior has its own default group.
        assertThat(seniorService.getManagedSeniors(scenario.assistant()))
                .extracting(ManagedSeniorResponse::seniorId)
                .containsExactly(scenario.mother().getSeniorId(), scenario.father().getSeniorId());
        assertThat(seniorService.getManagedSeniors(scenario.otherAssistant()))
                .extracting(ManagedSeniorResponse::seniorId)
                .containsExactly(scenario.grandmother().getSeniorId());
        assertThatThrownBy(() -> familyService.getConnectedSeniors(
                scenario.otherAssistant(), scenario.mother().getSeniorId()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED));
    }

    @Test
    void codeJoinRemovalAndRejoinChangeVisibilityWithoutChangingPhotoGroups() {
        FamilyScenario scenario = saveScenario(false, false);
        long groupCount = photoGroupRepository.count();
        long linkCount = photoGroupFamilyRepository.count();

        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).isEmpty();

        familyService.joinFamily(scenario.assistant(), joinRequest(scenario.fatherFamily().getSeniorCode()));
        flushAndClear();
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).extracting(ConnectedSeniorResponse::seniorId)
                .containsExactly(scenario.father().getSeniorId());

        familyService.removeFamilyMember(
                scenario.primary(), scenario.father().getSeniorId(), scenario.assistant().getUsersId()
        );
        flushAndClear();
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).isEmpty();

        familyService.joinFamily(scenario.assistant(), joinRequest(scenario.fatherFamily().getSeniorCode()));
        flushAndClear();
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).extracting(ConnectedSeniorResponse::seniorId)
                .containsExactly(scenario.father().getSeniorId());
        assertThat(photoGroupRepository.count()).isEqualTo(groupCount);
        assertThat(photoGroupFamilyRepository.count()).isEqualTo(linkCount);
    }

    @Test
    void primaryRoleInAnotherFamilyDoesNotBypassSelectedFamilySubRestriction() {
        FamilyScenario scenario = saveScenario(true, true);
        User user = userRepository.findById(scenario.assistant().getUsersId()).orElseThrow();
        assertThat(user.getManagerType()).isEqualTo(ManagerType.PRIMARY);

        assertThat(familyService.getConnectedSeniors(
                user, scenario.mother().getSeniorId()
        )).extracting(ConnectedSeniorResponse::seniorId)
                .containsExactly(scenario.father().getSeniorId());
    }

    @Test
    void changingSelectedFamilyRoleAppliesVisibilityOnTheNextRead() {
        FamilyScenario scenario = saveScenario(true, false);
        familyService.updatePrimaryManager(
                scenario.primary(), scenario.mother().getSeniorId(),
                primaryManagerRequest(scenario.assistant().getUsersId())
        );
        flushAndClear();

        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).extracting(ConnectedSeniorResponse::seniorId)
                .containsExactly(scenario.father().getSeniorId(), scenario.grandmother().getSeniorId());

        familyService.updatePrimaryManager(
                scenario.assistant(), scenario.mother().getSeniorId(),
                primaryManagerRequest(scenario.primary().getUsersId())
        );
        flushAndClear();
        assertThat(familyService.getConnectedSeniors(
                scenario.assistant(), scenario.mother().getSeniorId()
        )).extracting(ConnectedSeniorResponse::seniorId)
                .containsExactly(scenario.father().getSeniorId());
    }

    private FamilyScenario saveScenario(boolean managesFather, boolean primaryElsewhere) {
        return saveScenario(managesFather, primaryElsewhere, true);
    }

    private FamilyScenario saveScenario(boolean managesFather, boolean primaryElsewhere, boolean sharedConnections) {
        User primary = saveUser("child1");
        User assistant = saveUser("child2");
        User otherAssistant = saveUser("child3");
        Family motherFamily = saveFamily("MOM1-0001");
        Family fatherFamily = saveFamily("DAD1-0001");
        Family grandmotherFamily = saveFamily("GRAN-0001");
        saveMember(primary, motherFamily, ManagerType.PRIMARY);
        saveMember(primary, fatherFamily, ManagerType.PRIMARY);
        saveMember(primary, grandmotherFamily, ManagerType.PRIMARY);
        if (primaryElsewhere) {
            saveMember(assistant, saveFamily("OTHER001"), ManagerType.PRIMARY);
        }
        saveMember(assistant, motherFamily, ManagerType.SUB);
        if (managesFather) {
            saveMember(assistant, fatherFamily, ManagerType.SUB);
        }
        saveMember(otherAssistant, grandmotherFamily, ManagerType.SUB);
        Senior mother = saveSenior("엄마", SeniorRelation.MOTHER, motherFamily, primary);
        Senior father = saveSenior("아빠", SeniorRelation.FATHER, fatherFamily, primary);
        Senior grandmother = saveSenior("할머니", SeniorRelation.OTHER, grandmotherFamily, primary);
        if (sharedConnections) {
            share(motherFamily, fatherFamily);
            share(motherFamily, grandmotherFamily);
            share(fatherFamily, grandmotherFamily);
        }
        flushAndClear();
        return new FamilyScenario(primary, assistant, otherAssistant, fatherFamily, mother, father, grandmother);
    }

    private User saveUser(String loginId) {
        return userRepository.saveAndFlush(User.builder()
                .loginId(loginId).email(loginId + "@example.com")
                .name(loginId).role(Role.CHILD).build());
    }

    private Family saveFamily(String code) {
        Family family = familyRepository.saveAndFlush(Family.builder().seniorCode(code).build());
        PhotoGroup defaultGroup = photoGroupRepository.saveAndFlush(
                PhotoGroup.builder().name("Default " + code).build());
        saveGroupLink(family, defaultGroup);
        return family;
    }

    private void saveMember(User user, Family family, ManagerType managerType) {
        familyMemberRepository.saveAndFlush(FamilyMember.builder()
                .user(user).family(family).managerType(managerType).build());
    }

    private Senior saveSenior(String name, SeniorRelation relation, Family family, User primary) {
        return seniorRepository.saveAndFlush(Senior.builder()
                .name(name).relation(relation).family(family).registeredBy(primary)
                .birth(LocalDate.of(1950, 1, 1)).phoneNumber("01012345678").build());
    }

    private void share(Family first, Family second) {
        PhotoGroup group = photoGroupRepository.saveAndFlush(PhotoGroup.builder().name("Shared").build());
        saveGroupLink(first, group);
        saveGroupLink(second, group);
    }

    private void saveGroupLink(Family family, PhotoGroup group) {
        photoGroupFamilyRepository.saveAndFlush(PhotoGroupFamily.builder().family(family).photoGroup(group).build());
    }

    private FamilyJoinRequest joinRequest(String code) {
        FamilyJoinRequest request = new FamilyJoinRequest();
        ReflectionTestUtils.setField(request, "seniorCode", code);
        return request;
    }

    private FamilyPrimaryManagerUpdateRequest primaryManagerRequest(Long userId) {
        FamilyPrimaryManagerUpdateRequest request = new FamilyPrimaryManagerUpdateRequest();
        ReflectionTestUtils.setField(request, "targetUserId", userId);
        return request;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private record FamilyScenario(
            User primary, User assistant, User otherAssistant, Family fatherFamily,
            Senior mother, Senior father, Senior grandmother
    ) {}
}
