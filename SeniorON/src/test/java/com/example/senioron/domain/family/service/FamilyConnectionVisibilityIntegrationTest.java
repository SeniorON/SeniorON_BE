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
import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.dto.response.PhotoShareTargetResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.support.FamilyPhotoResponseMapper;
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
@Import({FamilyService.class, SeniorService.class, FamilyPhotoService.class,
        FamilyPhotoResponseMapper.class})
class FamilyConnectionVisibilityIntegrationTest {

    @Autowired private FamilyService familyService;
    @Autowired private SeniorService seniorService;
    @Autowired private FamilyPhotoService familyPhotoService;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private SeniorRepository seniorRepository;
    @Autowired private UserRepository userRepository;
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
        List<Long> seniorIds = List.of(
                scenario.father().getSeniorId(), scenario.grandmother().getSeniorId()
        );

        assertUploadForbidden(scenario.assistant(), scenario.mother(), seniorIds, multipart);

        verifyNoInteractions(photoPersistenceService, s3Service);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void membershipControlsUploadsToSelectedSeniors(boolean multipart) {
        FamilyScenario scenario = saveScenario(false, false);
        List<Long> seniorIds = List.of(scenario.mother().getSeniorId(), scenario.father().getSeniorId());

        assertUploadForbidden(scenario.assistant(), scenario.mother(), seniorIds, multipart);
        verifyNoInteractions(photoPersistenceService, s3Service);

        familyService.joinFamily(scenario.assistant(), joinRequest(scenario.fatherFamily().getSeniorCode()));
        flushAndClear();
        assertThat(upload(scenario.assistant(), scenario.mother(), seniorIds, multipart).getFamilyPhotoId())
                .isEqualTo(42L);

        familyService.removeFamilyMember(
                scenario.primary(), scenario.father().getSeniorId(), scenario.assistant().getUsersId()
        );
        flushAndClear();
        assertUploadForbidden(scenario.assistant(), scenario.mother(), seniorIds, multipart);

        familyService.joinFamily(scenario.assistant(), joinRequest(scenario.fatherFamily().getSeniorCode()));
        flushAndClear();
        assertThat(upload(scenario.assistant(), scenario.mother(), seniorIds, multipart).getFamilyPhotoId())
                .isEqualTo(42L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> seniors = ArgumentCaptor.forClass(List.class);
        verify(photoPersistenceService, times(2)).create(
                anyLong(), anyString(), anyString(), anyString(), any(), anyLong(), seniors.capture()
        );
        assertThat(seniors.getAllValues()).allSatisfy(value ->
                assertThat(value).containsExactlyElementsOf(seniorIds));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void assistantCanUploadToOnlyJoinedSenior(boolean multipart) {
        FamilyScenario scenario = saveScenario(false, false);
        Long motherId = scenario.mother().getSeniorId();

        assertThat(seniorService.getManagedSeniors(scenario.assistant()))
                .extracting(ManagedSeniorResponse::seniorId).containsExactly(scenario.mother().getSeniorId());
        assertThat(upload(scenario.assistant(), scenario.mother(), List.of(motherId), multipart)
                .getFamilyPhotoId()).isEqualTo(42L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void joiningTwoSeniorsAllowsSharingToOtherJoinedSenior(boolean multipart) {
        FamilyScenario scenario = saveScenario(true, false);
        assertThat(seniorService.getManagedSeniors(scenario.assistant()))
                .extracting(ManagedSeniorResponse::seniorId)
                .containsExactly(scenario.mother().getSeniorId(), scenario.father().getSeniorId());
        assertThat(upload(scenario.assistant(), scenario.mother(),
                List.of(scenario.father().getSeniorId()), multipart).getFamilyPhotoId()).isEqualTo(42L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void uploadRejectsUnmanagedSelectedSeniorEvenWhenRecipientIsManaged(boolean multipart) {
        FamilyScenario scenario = saveScenario(true, false);

        assertThatThrownBy(() -> upload(
                scenario.assistant(), scenario.grandmother(), List.of(scenario.mother().getSeniorId()), multipart
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED));
        verifyNoInteractions(photoPersistenceService, s3Service);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void changingSelectedFamilyRoleDoesNotGrantAccessToUnjoinedTarget(boolean multipart) {
        FamilyScenario scenario = saveScenario(true, true);
        List<Long> seniorIds = List.of(scenario.grandmother().getSeniorId());

        assertUploadForbidden(scenario.assistant(), scenario.mother(), seniorIds, multipart);
        verifyNoInteractions(photoPersistenceService, s3Service);

        familyService.updatePrimaryManager(
                scenario.primary(), scenario.mother().getSeniorId(),
                primaryManagerRequest(scenario.assistant().getUsersId())
        );
        flushAndClear();
        assertUploadForbidden(scenario.assistant(), scenario.mother(), seniorIds, multipart);

        familyService.updatePrimaryManager(
                scenario.assistant(), scenario.mother().getSeniorId(),
                primaryManagerRequest(scenario.primary().getUsersId())
        );
        flushAndClear();
        assertUploadForbidden(scenario.assistant(), scenario.mother(), seniorIds, multipart);
        verifyNoInteractions(photoPersistenceService, s3Service);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsUploadToManagedSeniorWithoutConnectedDevice(boolean multipart) {
        FamilyScenario scenario = saveScenario(false, false);
        Family unlinkedFamily = saveFamily("UNLINKED");
        saveMember(scenario.assistant(), unlinkedFamily, ManagerType.SUB);
        Senior unlinked = seniorRepository.saveAndFlush(Senior.builder()
                .name("이모").relation(SeniorRelation.OTHER).family(unlinkedFamily)
                .registeredBy(scenario.assistant())
                .birth(LocalDate.of(1950, 1, 1)).phoneNumber("01012345678").build());
        flushAndClear();

        assertThat(familyService.getPhotoShareTargets(scenario.assistant()))
                .extracting(PhotoShareTargetResponse::getSeniorId)
                .containsExactly(scenario.mother().getSeniorId());
        assertUploadForbidden(scenario.assistant(), scenario.mother(),
                List.of(scenario.mother().getSeniorId(), unlinked.getSeniorId()), multipart);
        verifyNoInteractions(photoPersistenceService, s3Service);
    }

    private void assertUploadForbidden(User user, Senior selected, List<Long> seniorIds, boolean multipart) {
        assertThatThrownBy(() -> upload(user, selected, seniorIds, multipart))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN));
    }

    private FamilyPhotoItemResponse upload(User user, Senior selected, List<Long> seniorIds, boolean multipart) {
        String imageKey = "family-photos/" + selected.getFamily().getFamilyId()
                + "/" + user.getUsersId() + "/" + UUID.randomUUID() + ".jpg";
        String idempotencyKey = UUID.randomUUID().toString();
        if (multipart) {
            given(s3Service.upload(any(), anyString())).willReturn(imageKey);
            FamilyPhotoCreateRequest request = new FamilyPhotoCreateRequest();
            request.setSeniorId(selected.getSeniorId());
            request.setSeniorIds(seniorIds);
            request.setImage(new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[]{1}));
            return familyPhotoService.createPhoto(user, idempotencyKey, request);
        }
        FamilyPhotoUploadCompleteRequest request = new FamilyPhotoUploadCompleteRequest();
        request.setSeniorId(selected.getSeniorId());
        request.setSeniorIds(seniorIds);
        request.setImageKey(imageKey);
        return familyPhotoService.completePhotoUpload(user, idempotencyKey, request);
    }

    private FamilyScenario saveScenario(boolean managesFather, boolean primaryElsewhere) {
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
        flushAndClear();
        return new FamilyScenario(primary, assistant, otherAssistant, fatherFamily, mother, father, grandmother);
    }

    private User saveUser(String loginId) {
        return userRepository.saveAndFlush(User.builder()
                .loginId(loginId).email(loginId + "@example.com")
                .name(loginId).role(Role.CHILD).build());
    }

    private Family saveFamily(String code) {
        return familyRepository.saveAndFlush(Family.builder().seniorCode(code).build());
    }

    private void saveMember(User user, Family family, ManagerType managerType) {
        familyMemberRepository.saveAndFlush(FamilyMember.builder()
                .user(user).family(family).managerType(managerType).build());
    }

    private Senior saveSenior(String name, SeniorRelation relation, Family family, User primary) {
        User parent = userRepository.saveAndFlush(User.builder()
                .loginId("parent-" + family.getSeniorCode())
                .email("parent-" + family.getSeniorCode() + "@example.com")
                .name(name).role(Role.PARENT).build());
        return seniorRepository.saveAndFlush(Senior.builder()
                .name(name).relation(relation).family(family).registeredBy(primary).parentUser(parent)
                .birth(LocalDate.of(1950, 1, 1)).phoneNumber("01012345678").build());
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
