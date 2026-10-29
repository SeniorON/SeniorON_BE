package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyPhotoCreateRequest;
import com.example.senioron.domain.family.dto.request.FamilyPhotoUploadCompleteRequest;
import com.example.senioron.domain.family.dto.request.FamilyPhotoUploadUrlRequest;
import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoUploadUrlResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoViewRepository;
import com.example.senioron.domain.family.support.FamilyPhotoResponseMapper;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.PresignedUploadInfo;
import com.example.senioron.global.storage.S3Service;
import com.example.senioron.global.storage.StoredObjectInfo;
import com.example.senioron.global.storage.ThumbnailService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FamilyPhotoPresignedUploadServiceTest {

    private static final Long FAMILY_ID = 3L;
    private static final Long SENIOR_ID = 7L;
    private static final Long OTHER_SENIOR_ID = 8L;
    private static final Long USER_ID = 10L;

    private static final String IMAGE_KEY =
            "family-photos/3/10/"
                    + "550e8400-e29b-41d4-a716-446655440000.jpg";

    private static final String UPLOAD_URL =
            "https://example-bucket.s3.amazonaws.com/"
                    + IMAGE_KEY;

    private static final String THUMBNAIL_KEY =
            "family-photos/thumbnails/family-photo.jpg";

    private final FamilyPhotoRepository familyPhotoRepository =
            mock(FamilyPhotoRepository.class);
    private final FamilyPhotoViewRepository familyPhotoViewRepository =
            mock(FamilyPhotoViewRepository.class);

    private final UserRepository userRepository =
            mock(UserRepository.class);

    private final S3Service s3Service =
            mock(S3Service.class);
    private final ThumbnailService thumbnailService =
            mock(ThumbnailService.class);

    private final FamilyPhotoPermissionService permissionService =
            mock(FamilyPhotoPermissionService.class);

    private final FamilyPhotoPersistenceService persistenceService =
            mock(FamilyPhotoPersistenceService.class);
    private final FamilyMemberRepository familyMemberRepository =
            mock(FamilyMemberRepository.class);
    private final SeniorRepository seniorRepository =
            mock(SeniorRepository.class);

    private final FamilyPhotoSeniorRepository familyPhotoSeniorRepository =
            mock(FamilyPhotoSeniorRepository.class);

    private FamilyPhotoService familyPhotoService;
    private final FamilyPhotoPushTargetService familyPhotoPushTargetService =
            mock(FamilyPhotoPushTargetService.class);
    private final ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);

    @BeforeEach
    void setUp() {
        doCallRealMethod().when(thumbnailService).validateOriginal(any());
        familyPhotoService = new FamilyPhotoService(
                familyPhotoRepository,
                familyPhotoViewRepository,
                userRepository,
                s3Service,
                thumbnailService,
                permissionService,
                persistenceService,
                familyMemberRepository,
                seniorRepository,
                familyPhotoPushTargetService,
                eventPublisher,
                new FamilyPhotoResponseMapper(s3Service),
                familyPhotoSeniorRepository
        );
    }

    @Test
    void createsPresignedUploadUrlForChild() {
        Family family = Family.builder()
                .familyId(FAMILY_ID)
                .seniorCode("TEST01")
                .build();
        Senior senior = Senior.builder()
                .seniorId(SENIOR_ID)
                .family(family)
                .build();

        User child = User.builder()
                .usersId(USER_ID)
                .family(family)
                .name("테스트 자녀")
                .role(Role.CHILD)
                .build();

        FamilyPhotoUploadUrlRequest request =
                new FamilyPhotoUploadUrlRequest();

        request.setContentType("image/jpeg");
        request.setFileSize(5L * 1024 * 1024);
        request.setSeniorId(SENIOR_ID);

        given(
                userRepository.findById(USER_ID)
        ).willReturn(
                Optional.of(child)
        );
        given(seniorRepository.findById(SENIOR_ID))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(child, family))
                .willReturn(true);

        given(
                s3Service.createPresignedUploadUrl(
                        "family-photos/3/10",
                        "image/jpeg"
                )
        ).willReturn(
                new PresignedUploadInfo(
                        IMAGE_KEY,
                        UPLOAD_URL,
                        300L
                )
        );

        FamilyPhotoUploadUrlResponse response =
                familyPhotoService.createPhotoUploadUrl(
                        child,
                        request
                );

        assertThat(response.getImageKey())
                .isEqualTo(IMAGE_KEY);

        assertThat(response.getUploadUrl())
                .isEqualTo(UPLOAD_URL);

        assertThat(response.getExpiresInSeconds())
                .isEqualTo(300L);

        verify(s3Service)
                .createPresignedUploadUrl(
                        "family-photos/3/10",
                        "image/jpeg"
                );
    }

    @Test
    void rejectsPresignedUploadUrlForParent() {
        Family family = Family.builder()
                .familyId(FAMILY_ID)
                .seniorCode("TEST01")
                .build();

        User parent = User.builder()
                .usersId(USER_ID)
                .family(family)
                .name("테스트 부모")
                .role(Role.PARENT)
                .build();

        FamilyPhotoUploadUrlRequest request =
                new FamilyPhotoUploadUrlRequest();

        request.setContentType("image/jpeg");
        request.setFileSize(5L * 1024 * 1024);
        request.setSeniorId(SENIOR_ID);

        given(
                userRepository.findById(USER_ID)
        ).willReturn(
                Optional.of(parent)
        );

        assertThatThrownBy(() ->
                familyPhotoService.createPhotoUploadUrl(
                        parent,
                        request
                )
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(s3Service);
    }

    @Test
    void rejectsPresignedUploadUrlWhenSeniorDoesNotExist() {
        User child = User.builder()
                .usersId(USER_ID)
                .name("테스트 자녀")
                .role(Role.CHILD)
                .build();
        FamilyPhotoUploadUrlRequest request =
                new FamilyPhotoUploadUrlRequest();
        request.setContentType("image/jpeg");
        request.setFileSize(5L * 1024 * 1024);
        request.setSeniorId(SENIOR_ID);

        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(child));
        given(seniorRepository.findById(SENIOR_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                familyPhotoService.createPhotoUploadUrl(
                        child,
                        request
                )
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_NOT_FOUND);

        verifyNoInteractions(s3Service);
    }

    @Test
    void rejectsPresignedUploadUrlForInaccessibleFamily() {
        Family family = createFamily();
        Senior senior = Senior.builder()
                .seniorId(SENIOR_ID)
                .family(family)
                .build();
        User child = createChild(family);
        FamilyPhotoUploadUrlRequest request =
                new FamilyPhotoUploadUrlRequest();
        request.setContentType("image/jpeg");
        request.setFileSize(5L * 1024 * 1024);
        request.setSeniorId(SENIOR_ID);

        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(child));
        given(seniorRepository.findById(SENIOR_ID))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(child, family))
                .willReturn(false);

        assertThatThrownBy(() ->
                familyPhotoService.createPhotoUploadUrl(
                        child,
                        request
                )
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED);

        verifyNoInteractions(s3Service);
    }

    @Test
    void rejectsCompletionForUnmanagedShareTarget() {
        Family family = createFamily();
        User child = createChild(family);
        Senior senior = Senior.builder()
                .seniorId(SENIOR_ID)
                .family(family)
                .build();
        FamilyPhotoUploadCompleteRequest request =
                createCompleteRequest("접근할 수 없는 그룹");

        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(child));
        given(seniorRepository.findById(SENIOR_ID))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(child, family))
                .willReturn(true);
        given(familyMemberRepository.findPhotoShareTargetMembers(
                child,
                List.of(ManagerType.PRIMARY, ManagerType.SUB)
        )).willReturn(List.of());
        stubPrimaryMember(child, family);

        assertThatThrownBy(() ->
                familyPhotoService.completePhotoUpload(
                        child,
                        "inaccessible-group-key",
                        request
                )
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN);

        verifyNoInteractions(persistenceService, s3Service);
    }

    @Test
    void rejectsImageKeyOwnedByAnotherUser() {
        Family family = Family.builder()
                .familyId(FAMILY_ID)
                .seniorCode("TEST01")
                .build();
        PhotoGroup photoGroup = createPhotoGroup();

        User child = User.builder()
                .usersId(USER_ID)
                .family(family)
                .name("테스트 자녀")
                .role(Role.CHILD)
                .build();

        FamilyPhotoUploadCompleteRequest request =
                createCompleteRequest("다른 사용자의 사진");

        request.setImageKey(
                "family-photos/3/999/"
                        + "550e8400-e29b-41d4-a716-446655440000.jpg"
        );

        String idempotencyKey =
                "53d006a9-7440-44cf-b12a-82d6dca64ed7";

        stubAccessibleUploadContext(family, child, photoGroup);

        given(
                persistenceService.findExisting(
                        USER_ID,
                        idempotencyKey
                )
        ).willReturn(
                Optional.empty()
        );

        assertThatThrownBy(() ->
                familyPhotoService.completePhotoUpload(
                        child,
                        idempotencyKey,
                        request
                )
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(
                        ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN
                );

        verifyNoInteractions(
                s3Service,
                familyPhotoRepository
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createsMultipartPhotoForSelectedSeniors(boolean thumbnailGenerated) {
        Family family = createFamily();
        PhotoGroup firstGroup = createPhotoGroup();
        PhotoGroup secondGroup = PhotoGroup.builder()
                .id(31L)
                .name("연결 가족 그룹")
                .build();
        User child = createChild(family);
        Senior senior = Senior.builder()
                .seniorId(SENIOR_ID)
                .family(family)
                .build();
        FamilyPhotoCreateRequest request =
                new FamilyPhotoCreateRequest();
        request.setSeniorId(SENIOR_ID);
        request.setSeniorIds(List.of(SENIOR_ID, OTHER_SENIOR_ID));
        request.setDescription("여러 가족 공유 사진");
        request.setImage(new MockMultipartFile(
                "image",
                "family-photo.jpg",
                "image/jpeg",
                FamilyPhotoImageFixtures.image("jpg")
        ));
        String idempotencyKey =
                "45c20ce1-c7d2-48ac-aa80-5031eedc09ad";
        FamilyPhoto savedPhoto = FamilyPhoto.builder()
                .familyPhotoId(23L)
                .photoGroup(firstGroup)
                .user(child)
                .imageKey(IMAGE_KEY)
                .description(request.getDescription())
                .idempotencyKey(idempotencyKey)
                .build();

        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(child));
        given(seniorRepository.findById(SENIOR_ID))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(child, family))
                .willReturn(true);
        Senior otherSenior = Senior.builder()
                .seniorId(OTHER_SENIOR_ID)
                .family(Family.builder().familyId(FAMILY_ID + 1).build())
                .build();
        given(familyMemberRepository.findPhotoShareTargetMembers(
                child,
                List.of(ManagerType.PRIMARY, ManagerType.SUB)
        )).willReturn(List.of(
                shareTargetMember(child, senior),
                shareTargetMember(child, otherSenior)
        ));
        stubPrimaryMember(child, family);
        given(persistenceService.findExisting(USER_ID, idempotencyKey))
                .willReturn(Optional.empty());
        given(s3Service.upload(
                request.getImage(),
                "family-photos/" + FAMILY_ID
        )).willReturn(IMAGE_KEY);
        byte[] originalBytes = FamilyPhotoImageFixtures.image("jpg");
        byte[] thumbnailBytes = {2};
        String expectedThumbnailKey = thumbnailGenerated ? THUMBNAIL_KEY : null;
        given(s3Service.download(IMAGE_KEY)).willReturn(originalBytes);
        if (thumbnailGenerated) {
            given(thumbnailService.create(originalBytes)).willReturn(thumbnailBytes);
            given(s3Service.uploadThumbnail(thumbnailBytes)).willReturn(THUMBNAIL_KEY);
        } else {
            given(thumbnailService.create(originalBytes))
                    .willThrow(new IllegalStateException("thumbnail generation failed"));
        }
        given(persistenceService.create(
                USER_ID,
                IMAGE_KEY,
                expectedThumbnailKey,
                idempotencyKey,
                request.getDescription(),
                FAMILY_ID,
                List.of(SENIOR_ID, OTHER_SENIOR_ID)
        )).willReturn(savedPhoto);

        FamilyPhotoItemResponse response = familyPhotoService.createPhoto(
                child,
                idempotencyKey,
                request
        );

        assertThat(response.getFamilyPhotoId()).isEqualTo(23L);
        verify(s3Service).download(IMAGE_KEY);
        verify(thumbnailService).create(originalBytes);
        if (thumbnailGenerated) {
            verify(s3Service).uploadThumbnail(thumbnailBytes);
        } else {
            verify(s3Service, never()).uploadThumbnail(
                    org.mockito.ArgumentMatchers.any(byte[].class)
            );
        }
        verify(persistenceService).create(
                USER_ID,
                IMAGE_KEY,
                expectedThumbnailKey,
                idempotencyKey,
                "여러 가족 공유 사진",
                FAMILY_ID,
                List.of(SENIOR_ID, OTHER_SENIOR_ID)
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deletesOriginalAndThumbnailWhenMultipartPersistenceFails(boolean integrityViolation) {
        Family family = createFamily();
        PhotoGroup photoGroup = createPhotoGroup();
        User child = createChild(family);
        FamilyPhotoCreateRequest request = new FamilyPhotoCreateRequest();
        request.setSeniorId(SENIOR_ID);
        request.setSeniorIds(List.of(SENIOR_ID));
        request.setDescription("저장 실패 사진");
        request.setImage(new MockMultipartFile(
                "image", "family-photo.jpg", "image/jpeg", FamilyPhotoImageFixtures.image("jpg")
        ));
        String idempotencyKey = "failed-multipart-upload";
        byte[] originalBytes = FamilyPhotoImageFixtures.image("jpg");
        byte[] thumbnailBytes = {2};
        RuntimeException failure = integrityViolation
                ? new DataIntegrityViolationException("saving failed")
                : new IllegalStateException("saving failed");

        stubAccessibleUploadContext(family, child, photoGroup);
        given(persistenceService.findExisting(USER_ID, idempotencyKey))
                .willReturn(Optional.empty());
        given(s3Service.upload(request.getImage(), "family-photos/" + FAMILY_ID))
                .willReturn(IMAGE_KEY);
        given(s3Service.download(IMAGE_KEY)).willReturn(originalBytes);
        given(thumbnailService.create(originalBytes)).willReturn(thumbnailBytes);
        given(s3Service.uploadThumbnail(thumbnailBytes)).willReturn(THUMBNAIL_KEY);
        given(persistenceService.create(
                USER_ID, IMAGE_KEY, THUMBNAIL_KEY, idempotencyKey,
                request.getDescription(), FAMILY_ID, List.of(SENIOR_ID)
        )).willThrow(failure);

        assertThatThrownBy(() -> familyPhotoService.createPhoto(
                child, idempotencyKey, request
        )).isSameAs(failure);

        verify(s3Service).delete(IMAGE_KEY);
        verify(s3Service).delete(THUMBNAIL_KEY);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void completesUploadedPhoto(boolean thumbnailGenerated) {
        Family family = createFamily();
        PhotoGroup photoGroup = createPhotoGroup();
        User child = createChild(family);
        String idempotencyKey =
                "ee3641b6-7f46-4ca7-a666-17b008f6f486";
        FamilyPhotoUploadCompleteRequest request =
                createCompleteRequest("오늘 찍은 사진");
        String expectedThumbnailKey = thumbnailGenerated ? THUMBNAIL_KEY : null;
        FamilyPhoto savedPhoto = FamilyPhoto.builder()
                .familyPhotoId(21L)
                .photoGroup(photoGroup)
                .user(child)
                .imageKey(IMAGE_KEY)
                .thumbnailKey(expectedThumbnailKey)
                .description(request.getDescription())
                .idempotencyKey(idempotencyKey)
                .build();

        stubAccessibleUploadContext(family, child, photoGroup);
        given(persistenceService.findExisting(USER_ID, idempotencyKey))
                .willReturn(Optional.empty());
        given(familyPhotoRepository.existsByImageKey(IMAGE_KEY))
                .willReturn(false);
        given(s3Service.findObjectInfo(IMAGE_KEY))
                .willReturn(Optional.of(
                        new StoredObjectInfo(
                                5L * 1024 * 1024,
                                "image/jpeg"
                        )
                ));
        byte[] originalBytes = FamilyPhotoImageFixtures.image("jpg");
        byte[] thumbnailBytes = {2};
        given(s3Service.download(IMAGE_KEY)).willReturn(originalBytes);
        if (thumbnailGenerated) {
            given(thumbnailService.create(originalBytes)).willReturn(thumbnailBytes);
            given(s3Service.uploadThumbnail(thumbnailBytes)).willReturn(THUMBNAIL_KEY);
        } else {
            given(thumbnailService.create(originalBytes))
                    .willThrow(new IllegalStateException("thumbnail generation failed"));
        }
        given(persistenceService.create(
                USER_ID,
                IMAGE_KEY,
                expectedThumbnailKey,
                idempotencyKey,
                request.getDescription(),
                FAMILY_ID,
                List.of(SENIOR_ID)
        )).willReturn(savedPhoto);
        given(s3Service.getFileUrl(IMAGE_KEY))
                .willReturn("https://example.com/" + IMAGE_KEY);
        given(permissionService.canDelete(savedPhoto, child))
                .willReturn(true);

        FamilyPhotoItemResponse response =
                familyPhotoService.completePhotoUpload(
                        child,
                        idempotencyKey,
                        request
                );

        assertThat(response.getFamilyPhotoId()).isEqualTo(21L);
        assertThat(response.getImageUrl())
                .isEqualTo("https://example.com/" + IMAGE_KEY);
        assertThat(response.getUploaderUserId()).isEqualTo(USER_ID);
        assertThat(response.getDescription()).isEqualTo("오늘 찍은 사진");
        assertThat(response.isCanDelete()).isTrue();

        verify(s3Service).findObjectInfo(IMAGE_KEY);
        verify(s3Service).download(IMAGE_KEY);
        verify(thumbnailService).create(originalBytes);
        if (thumbnailGenerated) {
            verify(s3Service).uploadThumbnail(thumbnailBytes);
        } else {
            verify(s3Service, never()).uploadThumbnail(
                    org.mockito.ArgumentMatchers.any(byte[].class)
            );
        }
        verify(persistenceService).create(
                USER_ID,
                IMAGE_KEY,
                expectedThumbnailKey,
                idempotencyKey,
                "오늘 찍은 사진",
                FAMILY_ID,
                List.of(SENIOR_ID)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"runtime", "integrity", "idempotency", "imageKey"})
    void cleansOnlyNewThumbnailWhenPresignedPersistenceFails(String failureCase) {
        Family family = createFamily();
        PhotoGroup photoGroup = createPhotoGroup();
        User child = createChild(family);
        String idempotencyKey = "failed-presigned-upload";
        FamilyPhotoUploadCompleteRequest request = createCompleteRequest("저장 실패 사진");
        FamilyPhoto existingPhoto = FamilyPhoto.builder()
                .familyPhotoId(24L)
                .photoGroup(photoGroup)
                .user(child)
                .imageKey(IMAGE_KEY)
                .thumbnailKey("family-photos/thumbnails/existing-photo.jpg")
                .build();
        RuntimeException failure = failureCase.equals("runtime")
                ? new IllegalStateException("saving failed")
                : new DataIntegrityViolationException("saving failed");

        stubAccessibleUploadContext(family, child, photoGroup);
        given(persistenceService.findExisting(USER_ID, idempotencyKey))
                .willReturn(Optional.empty(), failureCase.equals("idempotency")
                        ? Optional.of(existingPhoto) : Optional.empty());
        given(familyPhotoRepository.existsByImageKey(IMAGE_KEY))
                .willReturn(false, failureCase.equals("imageKey"));
        stubUploadedObjectAndThumbnail();
        given(persistenceService.create(
                USER_ID, IMAGE_KEY, THUMBNAIL_KEY, idempotencyKey,
                request.getDescription(), FAMILY_ID, List.of(SENIOR_ID)
        )).willThrow(failure);

        if (failureCase.equals("idempotency")) {
            FamilyPhotoItemResponse response = familyPhotoService.completePhotoUpload(
                    child, idempotencyKey, request
            );
            assertThat(response.getFamilyPhotoId()).isEqualTo(24L);
        } else if (failureCase.equals("imageKey")) {
            assertThatThrownBy(() -> familyPhotoService.completePhotoUpload(
                    child, idempotencyKey, request
            ))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(type(BusinessException.class))
                    .extracting(BusinessException::getCode)
                    .isEqualTo(ErrorCode.FAMILY_PHOTO_ALREADY_REGISTERED);
        } else {
            assertThatThrownBy(() -> familyPhotoService.completePhotoUpload(
                    child, idempotencyKey, request
            )).isSameAs(failure);
        }

        verify(s3Service).delete(THUMBNAIL_KEY);
        verify(s3Service, never()).delete(IMAGE_KEY);
        verify(s3Service, never()).delete(existingPhoto.getThumbnailKey());
    }

    @Test
    void preservesSavedObjectsWhenPresignedResponseCreationFails() {
        Family family = createFamily();
        PhotoGroup photoGroup = createPhotoGroup();
        User child = createChild(family);
        String idempotencyKey = "response-failure";
        FamilyPhotoUploadCompleteRequest request = createCompleteRequest("응답 실패 사진");
        FamilyPhoto savedPhoto = FamilyPhoto.builder()
                .familyPhotoId(25L)
                .photoGroup(photoGroup)
                .user(child)
                .imageKey(IMAGE_KEY)
                .thumbnailKey(THUMBNAIL_KEY)
                .build();
        RuntimeException failure = new IllegalStateException("URL generation failed");

        stubAccessibleUploadContext(family, child, photoGroup);
        given(persistenceService.findExisting(USER_ID, idempotencyKey))
                .willReturn(Optional.empty());
        stubUploadedObjectAndThumbnail();
        given(persistenceService.create(
                USER_ID, IMAGE_KEY, THUMBNAIL_KEY, idempotencyKey,
                request.getDescription(), FAMILY_ID, List.of(SENIOR_ID)
        )).willReturn(savedPhoto);
        given(s3Service.getFileUrl(IMAGE_KEY)).willThrow(failure);

        assertThatThrownBy(() -> familyPhotoService.completePhotoUpload(
                child, idempotencyKey, request
        )).isSameAs(failure);

        verify(s3Service, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsCompletionWhenUploadedObjectDoesNotExist() {
        Family family = createFamily();
        PhotoGroup photoGroup = createPhotoGroup();
        User child = createChild(family);
        String idempotencyKey =
                "98aec4cc-a658-43cd-830c-495e55c7147e";
        FamilyPhotoUploadCompleteRequest request =
                createCompleteRequest("업로드 실패 사진");

        stubAccessibleUploadContext(family, child, photoGroup);
        given(persistenceService.findExisting(USER_ID, idempotencyKey))
                .willReturn(Optional.empty());
        given(familyPhotoRepository.existsByImageKey(IMAGE_KEY))
                .willReturn(false);
        given(s3Service.findObjectInfo(IMAGE_KEY))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                familyPhotoService.completePhotoUpload(
                        child,
                        idempotencyKey,
                        request
                )
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_UPLOAD_NOT_FOUND);

        verify(persistenceService, never()).create(
                USER_ID,
                IMAGE_KEY,
                null,
                idempotencyKey,
                request.getDescription(),
                FAMILY_ID,
                List.of(SENIOR_ID)
        );
    }

    @Test
    void rejectsAndDeletesUploadedObjectLargerThanTenMegabytes() {
        Family family = createFamily();
        PhotoGroup photoGroup = createPhotoGroup();
        User child = createChild(family);
        String idempotencyKey =
                "aa3af3f9-9d30-49b8-bdb4-3b52dc7393cb";
        FamilyPhotoUploadCompleteRequest request =
                createCompleteRequest("너무 큰 사진");

        stubAccessibleUploadContext(family, child, photoGroup);
        given(persistenceService.findExisting(USER_ID, idempotencyKey))
                .willReturn(Optional.empty());
        given(familyPhotoRepository.existsByImageKey(IMAGE_KEY))
                .willReturn(false);
        given(s3Service.findObjectInfo(IMAGE_KEY))
                .willReturn(Optional.of(
                        new StoredObjectInfo(
                                10L * 1024 * 1024 + 1,
                                "image/jpeg"
                        )
                ));

        assertThatThrownBy(() ->
                familyPhotoService.completePhotoUpload(
                        child,
                        idempotencyKey,
                        request
                )
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_SIZE_EXCEEDED);

        verify(s3Service).delete(IMAGE_KEY);
        verify(persistenceService, never()).create(
                USER_ID,
                IMAGE_KEY,
                null,
                idempotencyKey,
                request.getDescription(),
                FAMILY_ID,
                List.of(SENIOR_ID)
        );
    }

    @Test
    void returnsExistingPhotoForRepeatedIdempotencyKey() {
        Family family = createFamily();
        PhotoGroup photoGroup = createPhotoGroup();
        User child = createChild(family);
        String idempotencyKey =
                "02249e31-bc8b-4204-a846-ddd840ab09c6";
        FamilyPhotoUploadCompleteRequest request =
                createCompleteRequest("재요청 사진");
        FamilyPhoto existingPhoto = FamilyPhoto.builder()
                .familyPhotoId(22L)
                .photoGroup(photoGroup)
                .user(child)
                .imageKey(IMAGE_KEY)
                .description("처음 저장된 설명")
                .idempotencyKey(idempotencyKey)
                .build();

        stubAccessibleUploadContext(family, child, photoGroup);
        given(persistenceService.findExisting(USER_ID, idempotencyKey))
                .willReturn(Optional.of(existingPhoto));
        given(s3Service.getFileUrl(IMAGE_KEY))
                .willReturn("https://example.com/" + IMAGE_KEY);

        FamilyPhotoItemResponse response =
                familyPhotoService.completePhotoUpload(
                        child,
                        idempotencyKey,
                        request
                );

        assertThat(response.getFamilyPhotoId()).isEqualTo(22L);
        assertThat(response.getDescription()).isEqualTo("처음 저장된 설명");

        verify(s3Service, never()).findObjectInfo(IMAGE_KEY);
        verify(s3Service, never()).download(IMAGE_KEY);
        verifyNoInteractions(thumbnailService);
        verify(persistenceService, never()).create(
                USER_ID,
                IMAGE_KEY,
                null,
                idempotencyKey,
                request.getDescription(),
                FAMILY_ID,
                List.of(SENIOR_ID)
        );
    }

    private Family createFamily() {
        return Family.builder()
                .familyId(FAMILY_ID)
                .seniorCode("TEST01")
                .build();
    }

    private PhotoGroup createPhotoGroup() {
        return PhotoGroup.builder()
                .id(30L)
                .name("Family " + FAMILY_ID)
                .build();
    }

    private User createChild(Family family) {
        return User.builder()
                .usersId(USER_ID)
                .family(family)
                .name("테스트 자녀")
                .role(Role.CHILD)
                .build();
    }

    private FamilyPhotoUploadCompleteRequest createCompleteRequest(
            String description
    ) {
        FamilyPhotoUploadCompleteRequest request =
                new FamilyPhotoUploadCompleteRequest();
        request.setSeniorId(SENIOR_ID);
        request.setSeniorIds(List.of(SENIOR_ID));
        request.setImageKey(IMAGE_KEY);
        request.setDescription(description);
        return request;
    }

    private void stubUploadedObjectAndThumbnail() {
        byte[] originalBytes = FamilyPhotoImageFixtures.image("jpg");
        byte[] thumbnailBytes = {2};
        given(s3Service.findObjectInfo(IMAGE_KEY))
                .willReturn(Optional.of(new StoredObjectInfo(1024L, "image/jpeg")));
        given(s3Service.download(IMAGE_KEY)).willReturn(originalBytes);
        given(thumbnailService.create(originalBytes)).willReturn(thumbnailBytes);
        given(s3Service.uploadThumbnail(thumbnailBytes)).willReturn(THUMBNAIL_KEY);
    }

    private void stubAccessibleUploadContext(
            Family family,
            User child,
            PhotoGroup photoGroup
    ) {
        Senior senior = Senior.builder()
                .seniorId(SENIOR_ID)
                .family(family)
                .build();

        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(child));
        given(seniorRepository.findById(SENIOR_ID))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(child, family))
                .willReturn(true);
        stubPrimaryMember(child, family);
        given(familyMemberRepository.findPhotoShareTargetMembers(
                child,
                List.of(ManagerType.PRIMARY, ManagerType.SUB)
        )).willReturn(List.of(shareTargetMember(child, senior)));
    }

    private FamilyMember shareTargetMember(User child, Senior senior) {
        ReflectionTestUtils.setField(senior.getFamily(), "senior", senior);
        return FamilyMember.builder()
                .user(child)
                .family(senior.getFamily())
                .managerType(ManagerType.PRIMARY)
                .build();
    }

    private void stubPrimaryMember(User child, Family family) {
        given(familyMemberRepository.findByUserAndFamily(child, family))
                .willReturn(Optional.of(FamilyMember.builder()
                        .user(child)
                        .family(family)
                        .managerType(ManagerType.PRIMARY)
                        .build()));
    }
}
