package com.example.senioron.domain.family.service;

import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.family.dto.request.FamilyPhotoCreateRequest;
import com.example.senioron.domain.family.dto.request.FamilyPhotoUploadCompleteRequest;
import com.example.senioron.domain.family.dto.request.FamilyPrimaryManagerUpdateRequest;
import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.dto.response.PhotoShareTargetResponse;
import com.example.senioron.domain.family.support.FamilyPhotoResponseMapper;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import com.example.senioron.global.storage.StoredObjectInfo;
import com.example.senioron.global.storage.ThumbnailService;
import com.example.senioron.domain.family.event.FamilyPhotoSharedEvent;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RecordApplicationEvents
@DataJpaTest(showSql = false)
@Import({FamilyService.class, FamilyPhotoService.class, FamilyPhotoPersistenceService.class,
        FamilyPhotoPermissionService.class, ThumbnailService.class,
        FamilyPhotoResponseMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Timeout(30)
class FamilyPhotoUploadAuthorizationIntegrationTest {

    @Autowired private FamilyService familyService;
    @Autowired private FamilyPhotoService photoService;
    @Autowired private FamilyPhotoPersistenceService persistenceService;
    @Autowired private FamilyPhotoRepository photos;
    @Autowired private FamilyMemberRepository members;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private FamilyPhotoSeniorRepository shareTargets;
    @MockitoBean private S3Service storage;
    @MockitoSpyBean private ThumbnailService thumbnails;
    @MockitoBean private DeviceService devices;
    @MockitoBean private FamilyPhotoPushTargetService pushTargets;

    @Autowired private ApplicationEvents events;

    private byte[] uploadBytes;
    private String uploadContentType;
    private Fixture fixture;
    private String imageKey;
    private String thumbnailKey;

    @BeforeEach
    void setUp() {
        fixture = transaction().execute(status -> {
            User primaryA = saveUser(), primaryB = saveUser(), uploader = saveUser();
            Family familyA = saveFamily(), familyB = saveFamily();
            saveMember(primaryA, familyA, ManagerType.PRIMARY);
            saveMember(primaryB, familyB, ManagerType.PRIMARY);
            saveMember(uploader, familyA, ManagerType.SUB);
            saveMember(uploader, familyB, ManagerType.SUB);
            User parentA = saveUser(Role.PARENT), parentB = saveUser(Role.PARENT);
            Senior seniorA = saveSenior(familyA, primaryA, parentA), seniorB = saveSenior(familyB, primaryB, parentB);
            entityManager.flush();
            return new Fixture(primaryA, primaryB, uploader, parentA, parentB, familyA, familyB,
                    seniorA, seniorB);
        });
        imageKey = "family-photos/" + fixture.familyA().getFamilyId() + "/"
                + fixture.uploader().getUsersId() + "/" + UUID.randomUUID() + ".jpg";
        thumbnailKey = "family-photos/thumbnails/" + UUID.randomUUID() + ".jpg";
        uploadBytes = FamilyPhotoImageFixtures.image("jpg");
        uploadContentType = "image/jpeg";
        given(storage.upload(any(), anyString())).willReturn(imageKey);
        given(storage.findObjectInfo(anyString()))
                .willReturn(Optional.of(new StoredObjectInfo(100L, "image/jpeg")));
        given(storage.download(anyString())).willReturn(uploadBytes);
        doReturn(new byte[]{2}).when(thumbnails).create(any());
        given(storage.uploadThumbnail(any())).willReturn(thumbnailKey);
    }

    @AfterEach
    void cleanCommittedFixture() {
        if (fixture == null) {
            return;
        }
        transaction().executeWithoutResult(status -> {
            List<Long> userIds = List.of(fixture.primaryA().getUsersId(), fixture.primaryB().getUsersId(),
                    fixture.uploader().getUsersId());
            List<Long> parentIds = List.of(fixture.parentA().getUsersId(), fixture.parentB().getUsersId());
            List<Long> familyIds = List.of(fixture.familyA().getFamilyId(), fixture.familyB().getFamilyId());
            entityManager.createQuery("DELETE FROM FamilyPhotoView v WHERE v.familyPhoto.user.usersId IN :ids")
                    .setParameter("ids", userIds).executeUpdate();
            entityManager.createQuery("DELETE FROM FamilyPhotoSenior t WHERE t.familyPhoto.user.usersId IN :ids")
                    .setParameter("ids", userIds).executeUpdate();
            entityManager.createQuery("DELETE FROM FamilyPhoto p WHERE p.user.usersId IN :ids")
                    .setParameter("ids", userIds).executeUpdate();
            entityManager.createQuery("DELETE FROM Senior s WHERE s.family.familyId IN :ids")
                    .setParameter("ids", familyIds).executeUpdate();
            entityManager.createQuery("DELETE FROM FamilyMember m WHERE m.family.familyId IN :ids")
                    .setParameter("ids", familyIds).executeUpdate();
            entityManager.createQuery("DELETE FROM Family f WHERE f.familyId IN :ids")
                    .setParameter("ids", familyIds).executeUpdate();
            entityManager.createQuery("DELETE FROM User u WHERE u.usersId IN :ids")
                    .setParameter("ids", userIds).executeUpdate();
            entityManager.createQuery("DELETE FROM User u WHERE u.usersId IN :ids")
                    .setParameter("ids", parentIds).executeUpdate();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void savesBothUploadPathsAndAllRequestedSeniorsWhenAccessRemains(boolean multipart) {
        var response = upload(fixture.uploader(), multipart);
        assertSavedForBothSeniors(response.getFamilyPhotoId());
    }

    @ParameterizedTest
    @CsvSource({"UNLINK_TARGET_DEVICE,true", "UNLINK_TARGET_DEVICE,false", "REMOVE_TARGET,true",
            "REMOVE_TARGET,false", "REMOVE_SELECTED,true", "REMOVE_SELECTED,false"})
    void rejectsEntireUploadWhenAccessIsRevokedDuringThumbnailWork(Revocation revocation, boolean multipart) {
        User uploader = fixture.uploader();
        // The presigned key must belong to whichever uploader this scenario uses.
        imageKey = "family-photos/" + fixture.familyA().getFamilyId() + "/" + uploader.getUsersId()
                + "/" + UUID.randomUUID() + ".jpg";
        given(storage.upload(any(), anyString())).willReturn(imageKey);
        given(storage.download(imageKey)).willAnswer(invocation -> {
            revoke(revocation);
            return FamilyPhotoImageFixtures.image("jpg");
        });

        ErrorCode expected = revocation == Revocation.REMOVE_SELECTED
                ? ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED : ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN;
        assertThatThrownBy(() -> upload(uploader, multipart))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(expected));
        assertRevoked(revocation);
        assertThat(photos.countAccessibleByFamily(fixture.familyA())).isZero();
        assertThat(photos.countAccessibleByFamily(fixture.familyB())).isZero();
        verify(storage).delete(thumbnailKey);
        if (multipart) {
            verify(storage).delete(imageKey);
        } else {
            verify(storage, never()).delete(imageKey);
        }
    }

    @ParameterizedTest
    @CsvSource({"REMOVE_TARGET,true", "REMOVE_TARGET,false"})
    void serializesSavingAndRevocationInBothOrders(Revocation revocation, boolean revocationFirst) throws Exception {
        CountDownLatch holderReady = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        CountDownLatch contenderStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        if (!revocationFirst) {
            // Pause inside the real save transaction after all share targets have reached the DB.
            doAnswer(invocation -> {
                Iterable<FamilyPhotoSenior> requestedTargets = invocation.getArgument(0);
                List<FamilyPhotoSenior> result = shareTargets.saveAll(requestedTargets);
                shareTargets.flush();
                holderReady.countDown();
                await(releaseHolder);
                return result;
            }).when(shareTargets).saveAllAndFlush(any());
        }
        try {
            Future<?> holder = executor.submit(() -> {
                if (revocationFirst) {
                    transaction().executeWithoutResult(status -> {
                        revoke(revocation);
                        holderReady.countDown();
                        await(releaseHolder);
                    });
                } else {
                    savePhoto();
                }
            });
            if (!holderReady.await(5, TimeUnit.SECONDS)) {
                holder.get(1, TimeUnit.SECONDS);
                fail("The first operation did not reach the transaction pause point");
            }
            Future<?> contender = executor.submit(() -> {
                contenderStarted.countDown();
                if (revocationFirst) {
                    savePhoto();
                } else {
                    revoke(revocation);
                }
            });
            assertThat(contenderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> contender.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            releaseHolder.countDown();
            holder.get(5, TimeUnit.SECONDS);
            if (revocationFirst) {
                assertThatThrownBy(() -> contender.get(5, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .cause().isInstanceOfSatisfying(BusinessException.class, exception ->
                                assertThat(exception.getCode()).isEqualTo(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN));
            } else {
                contender.get(5, TimeUnit.SECONDS);
            }
            assertRevoked(revocation);
            // Previously committed shares survive revocation; a later save is rejected.
            assertThat(photos.countAccessibleByFamily(fixture.familyB())).isEqualTo(revocationFirst ? 0 : 1);
        } finally {
            releaseHolder.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void photoShareTargetsIncludeOnlyManagedSeniors() {
        var targets = familyService.getPhotoShareTargets(fixture.uploader());
        assertThat(targets).extracting(target -> target.getSeniorId())
                .containsExactly(fixture.seniorA().getSeniorId(), fixture.seniorB().getSeniorId());

        // 주담당자도 본인이 가입하지 않은 상대 가족은 대상 목록에 나오지 않는다.
        assertThat(familyService.getPhotoShareTargets(fixture.primaryA()))
                .extracting(target -> target.getSeniorId())
                .containsExactly(fixture.seniorA().getSeniorId());

        revoke(Revocation.REMOVE_TARGET);
        assertThat(familyService.getPhotoShareTargets(fixture.uploader()))
                .extracting(target -> target.getSeniorId())
                .containsExactly(fixture.seniorA().getSeniorId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void changingPrimaryToSubDuringUploadKeepsJoinedFamilyAccess(boolean multipart) {
        FamilyPrimaryManagerUpdateRequest promote = new FamilyPrimaryManagerUpdateRequest();
        ReflectionTestUtils.setField(promote, "targetUserId", fixture.uploader().getUsersId());
        familyService.updatePrimaryManager(fixture.primaryA(), fixture.seniorA().getSeniorId(), promote);
        given(storage.download(imageKey)).willAnswer(invocation -> {
            FamilyPrimaryManagerUpdateRequest demote = new FamilyPrimaryManagerUpdateRequest();
            ReflectionTestUtils.setField(demote, "targetUserId", fixture.primaryA().getUsersId());
            familyService.updatePrimaryManager(fixture.uploader(), fixture.seniorA().getSeniorId(), demote);
            return FamilyPhotoImageFixtures.image("jpg");
        });

        upload(fixture.uploader(), multipart);
        assertThat(members.findByUserAndFamily(fixture.uploader(), fixture.familyA()).orElseThrow()
                .getManagerType()).isEqualTo(ManagerType.SUB);
        assertThat(photos.countAccessibleByFamily(fixture.familyA())).isEqualTo(1);
        assertThat(photos.countAccessibleByFamily(fixture.familyB())).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void savesToBothSeniorsWithoutSharingConnection(boolean multipart) {
        List<Long> seniorIds = List.of(fixture.seniorA().getSeniorId(), fixture.seniorB().getSeniorId());
        assertThat(familyService.getPhotoShareTargets(fixture.uploader()))
                .extracting(PhotoShareTargetResponse::getSeniorId).containsExactlyElementsOf(seniorIds);

        var response = upload(fixture.uploader(), multipart, seniorIds);
        assertThat(photos.countAccessibleByFamily(fixture.familyA())).isEqualTo(1);
        assertThat(photos.countAccessibleByFamily(fixture.familyB())).isEqualTo(1);
        assertThat(savedSeniorIds(response.getFamilyPhotoId())).containsExactlyInAnyOrderElementsOf(seniorIds);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsEntireUploadToUnjoinedSenior(boolean multipart) {
        revoke(Revocation.REMOVE_TARGET);
        assertThatThrownBy(() -> upload(fixture.uploader(), multipart,
                List.of(fixture.seniorA().getSeniorId(), fixture.seniorB().getSeniorId())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN));
        assertThat(photos.countAccessibleByFamily(fixture.familyA())).isZero();
        assertThat(photos.countAccessibleByFamily(fixture.familyB())).isZero();
        verify(storage, never()).upload(any(), anyString());
        verify(storage, never()).findObjectInfo(anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void recheckMembershipImmediatelyBeforeSaving(boolean multipart) {
        given(storage.download(imageKey)).willAnswer(invocation -> {
            revoke(Revocation.REMOVE_TARGET);
            return FamilyPhotoImageFixtures.image("jpg");
        });
        assertThatThrownBy(() -> upload(fixture.uploader(), multipart,
                List.of(fixture.seniorA().getSeniorId(), fixture.seniorB().getSeniorId())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN));
        assertThat(photos.countAccessibleByFamily(fixture.familyA())).isZero();
        assertThat(photos.countAccessibleByFamily(fixture.familyB())).isZero();
        verify(storage).delete(thumbnailKey);
    }

    @ParameterizedTest
    @CsvSource({"true,text", "false,text", "true,jpg", "false,jpg", "true,png", "false,png"})
    void rejectsInvalidOriginalWithoutPhotoMappingOrSharedEvent(boolean multipart, String kind) {
        uploadBytes = kind.equals("text") ? "not an image".getBytes(StandardCharsets.UTF_8)
                : Arrays.copyOf(FamilyPhotoImageFixtures.image(kind), 30);
        given(storage.download(imageKey)).willReturn(uploadBytes);

        assertThatThrownBy(() -> upload(fixture.uploader(), multipart))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.FAMILY_PHOTO_INVALID_IMAGE);
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                });

        assertNoPhotoSideEffects();
        verify(thumbnails, never()).create(any());
        verify(storage, never()).uploadThumbnail(any());
        if (multipart) {
            verify(storage).delete(imageKey);
        } else {
            verify(storage, never()).delete(imageKey);
        }
    }

    @ParameterizedTest
    @CsvSource({"true,jpg", "false,jpg", "true,png", "false,png", "true,webp", "false,webp"})
    void registersDecodableFormatsForMultipleSeniors(boolean multipart, String format) {
        uploadBytes = FamilyPhotoImageFixtures.image(format);
        uploadContentType = format.equals("jpg") ? "image/jpeg" : "image/" + format;
        imageKey = imageKey.substring(0, imageKey.lastIndexOf('.') + 1) + format;
        given(storage.upload(any(), anyString())).willReturn(imageKey);
        given(storage.download(imageKey)).willReturn(uploadBytes);
        given(storage.findObjectInfo(imageKey)).willReturn(Optional.of(new StoredObjectInfo(
                uploadBytes.length, uploadContentType)));
        doCallRealMethod().when(thumbnails).create(any());

        var response = upload(fixture.uploader(), multipart);

        assertThat(photos.findById(response.getFamilyPhotoId()).orElseThrow().getThumbnailKey())
                .isEqualTo(thumbnailKey);
        assertSavedForBothSeniors(response.getFamilyPhotoId());
        assertThat(sharedEventCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"true,generation", "false,generation", "true,storage", "false,storage"})
    void keepsValidOriginalWhenOnlyThumbnailFails(boolean multipart, String failure) {
        if (failure.equals("generation")) {
            doThrow(new IllegalStateException("resize failed")).when(thumbnails).create(any());
        } else {
            given(storage.uploadThumbnail(any())).willThrow(new IllegalStateException("S3 put failed"));
        }

        var response = upload(fixture.uploader(), multipart);

        assertThat(photos.findById(response.getFamilyPhotoId()).orElseThrow().getThumbnailKey()).isNull();
        assertSavedForBothSeniors(response.getFamilyPhotoId());
        assertThat(sharedEventCount()).isEqualTo(1);
        verify(storage, never()).delete(imageKey);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void downloadFailureDoesNotRegisterUnverifiedOriginal(boolean multipart) {
        RuntimeException failure = new IllegalStateException("S3 get failed");
        given(storage.download(imageKey)).willThrow(failure);

        assertThatThrownBy(() -> upload(fixture.uploader(), multipart)).isSameAs(failure);

        assertNoPhotoSideEffects();
        if (multipart) {
            verify(storage).delete(imageKey);
        } else {
            verify(storage, never()).delete(imageKey);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void repeatedRequestReturnsSavedPhotoWithoutRevalidationOrDuplicateEvent(boolean multipart) {
        String idempotencyKey = UUID.randomUUID().toString();
        List<Long> seniorIds = List.of(fixture.seniorA().getSeniorId(), fixture.seniorB().getSeniorId());
        var first = upload(fixture.uploader(), multipart, seniorIds, idempotencyKey);
        given(storage.download(imageKey)).willThrow(new IllegalStateException("temporary S3 failure"));

        var repeated = upload(fixture.uploader(), multipart, seniorIds, idempotencyKey);

        assertThat(repeated.getFamilyPhotoId()).isEqualTo(first.getFamilyPhotoId());
        verify(storage, times(1)).download(imageKey);
        verify(storage, never()).delete(imageKey);
        assertThat(sharedEventCount()).isEqualTo(1);
        assertSavedForBothSeniors(first.getFamilyPhotoId());

        revoke(Revocation.REMOVE_TARGET);
        assertThatThrownBy(() -> upload(fixture.uploader(), multipart, seniorIds, idempotencyKey))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN));
        verify(storage, never()).delete(imageKey);
    }

    private void assertNoPhotoSideEffects() {
        assertThat(photos.countAccessibleByFamily(fixture.familyA())).isZero();
        assertThat(photos.countAccessibleByFamily(fixture.familyB())).isZero();
        Long shareTargetCount = transaction().execute(status -> shareTargets.findAll().stream()
                .filter(target -> target.getFamilyPhoto().getUser().getUsersId()
                        .equals(fixture.uploader().getUsersId())).count());
        assertThat(shareTargetCount).isZero();
        assertThat(sharedEventCount()).isZero();
    }

    private void assertSavedForBothSeniors(Long photoId) {
        assertThat(photos.countAccessibleByFamily(fixture.familyA())).isEqualTo(1);
        assertThat(photos.countAccessibleByFamily(fixture.familyB())).isEqualTo(1);
        assertThat(savedSeniorIds(photoId))
                .containsExactlyInAnyOrder(fixture.seniorA().getSeniorId(), fixture.seniorB().getSeniorId());
    }

    private List<Long> savedSeniorIds(Long photoId) {
        return transaction().execute(status -> shareTargets.findAll().stream()
                .filter(target -> target.getFamilyPhoto().getFamilyPhotoId().equals(photoId))
                .map(target -> target.getSenior().getSeniorId()).toList());
    }

    private long sharedEventCount() {
        return events.stream(FamilyPhotoSharedEvent.class).count();
    }

    private void savePhoto() {
        persistenceService.create(fixture.uploader().getUsersId(), imageKey, thumbnailKey,
                UUID.randomUUID().toString(), null, fixture.familyA().getFamilyId(), List.of(fixture.seniorB().getSeniorId()));
    }

    private FamilyPhotoItemResponse upload(User uploader, boolean multipart) {
        List<Long> seniorIds = List.of(fixture.seniorA().getSeniorId(), fixture.seniorB().getSeniorId());
        return upload(uploader, multipart, seniorIds);
    }

    private FamilyPhotoItemResponse upload(User uploader, boolean multipart, List<Long> seniorIds) {
        return upload(uploader, multipart, seniorIds, UUID.randomUUID().toString());
    }

    private FamilyPhotoItemResponse upload(User uploader, boolean multipart, List<Long> seniorIds,
                                          String idempotencyKey) {
        if (multipart) {
            FamilyPhotoCreateRequest request = new FamilyPhotoCreateRequest();
            request.setSeniorId(fixture.seniorA().getSeniorId());
            request.setSeniorIds(seniorIds);
            request.setImage(new MockMultipartFile("image", "photo" + imageKey.substring(imageKey.lastIndexOf('.')),
                    uploadContentType, uploadBytes));
            return photoService.createPhoto(uploader, idempotencyKey, request);
        }
        FamilyPhotoUploadCompleteRequest request = new FamilyPhotoUploadCompleteRequest();
        request.setSeniorId(fixture.seniorA().getSeniorId());
        request.setSeniorIds(seniorIds);
        request.setImageKey(imageKey);
        return photoService.completePhotoUpload(uploader, idempotencyKey, request);
    }

    private void revoke(Revocation revocation) {
        switch (revocation) {
            case REMOVE_TARGET -> familyService.removeFamilyMember(fixture.primaryB(), fixture.seniorB().getSeniorId(),
                    fixture.uploader().getUsersId());
            case REMOVE_SELECTED -> familyService.removeFamilyMember(fixture.primaryA(), fixture.seniorA().getSeniorId(),
                    fixture.uploader().getUsersId());
            case UNLINK_TARGET_DEVICE -> transaction().executeWithoutResult(status -> entityManager
                    .createQuery("UPDATE Senior s SET s.parentUser = null WHERE s.seniorId = :id")
                    .setParameter("id", fixture.seniorB().getSeniorId()).executeUpdate());
        }
    }

    private void assertRevoked(Revocation revocation) {
        switch (revocation) {
            case REMOVE_TARGET -> assertThat(members.existsByUserAndFamily(fixture.uploader(), fixture.familyB())).isFalse();
            case REMOVE_SELECTED -> assertThat(members.existsByUserAndFamily(fixture.uploader(), fixture.familyA())).isFalse();
            case UNLINK_TARGET_DEVICE -> assertThat(familyService.getPhotoShareTargets(fixture.uploader()))
                    .extracting(PhotoShareTargetResponse::getSeniorId)
                    .containsExactly(fixture.seniorA().getSeniorId());
        }
    }

    private User saveUser() {
        return saveUser(Role.CHILD);
    }

    private User saveUser(Role role) {
        String id = UUID.randomUUID().toString();
        User user = User.builder().loginId(id).email(id + "@example.com").name("업로드 권한 테스트").role(role).build();
        entityManager.persist(user);
        return user;
    }

    private Family saveFamily() {
        Family family = Family.builder().seniorCode(UUID.randomUUID().toString().substring(0, 9)).build();
        entityManager.persist(family);
        return family;
    }

    private void saveMember(User user, Family family, ManagerType type) {
        entityManager.persist(FamilyMember.builder().user(user).family(family).managerType(type).build());
    }

    private Senior saveSenior(Family family, User primary, User parent) {
        Senior senior = Senior.builder().family(family).registeredBy(primary).parentUser(parent).name("권한 테스트")
                .relation(SeniorRelation.OTHER).birth(LocalDate.of(1950, 1, 1)).phoneNumber("01012345678").build();
        entityManager.persist(senior);
        return senior;
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private enum Revocation { REMOVE_TARGET, REMOVE_SELECTED, UNLINK_TARGET_DEVICE }

    private record Fixture(User primaryA, User primaryB, User uploader, User parentA, User parentB,
                           Family familyA, Family familyB,
                           Senior seniorA, Senior seniorB) {}
}
