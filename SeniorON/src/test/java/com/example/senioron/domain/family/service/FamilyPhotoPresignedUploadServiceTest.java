package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyPhotoUploadCompleteRequest;
import com.example.senioron.domain.family.dto.request.FamilyPhotoUploadUrlRequest;
import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoUploadUrlResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.PresignedUploadInfo;
import com.example.senioron.global.storage.S3Service;
import com.example.senioron.global.storage.StoredObjectInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FamilyPhotoPresignedUploadServiceTest {

    private static final Long FAMILY_ID = 3L;
    private static final Long SENIOR_ID = 7L;
    private static final Long USER_ID = 10L;

    private static final String IMAGE_KEY =
            "family-photos/3/10/"
                    + "550e8400-e29b-41d4-a716-446655440000.jpg";

    private static final String UPLOAD_URL =
            "https://example-bucket.s3.amazonaws.com/"
                    + IMAGE_KEY;

    private final FamilyPhotoRepository familyPhotoRepository =
            mock(FamilyPhotoRepository.class);

    private final UserRepository userRepository =
            mock(UserRepository.class);

    private final S3Service s3Service =
            mock(S3Service.class);

    private final FamilyPhotoPermissionService permissionService =
            mock(FamilyPhotoPermissionService.class);

    private final FamilyPhotoPersistenceService persistenceService =
            mock(FamilyPhotoPersistenceService.class);
    private final FamilyMemberRepository familyMemberRepository =
            mock(FamilyMemberRepository.class);
    private final SeniorRepository seniorRepository =
            mock(SeniorRepository.class);
    private final PhotoGroupFamilyRepository photoGroupFamilyRepository =
            mock(PhotoGroupFamilyRepository.class);

    private FamilyPhotoService familyPhotoService;

    @BeforeEach
    void setUp() {
        familyPhotoService = new FamilyPhotoService(
                familyPhotoRepository,
                userRepository,
                s3Service,
                permissionService,
                persistenceService,
                familyMemberRepository,
                seniorRepository,
                photoGroupFamilyRepository
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
    void rejectsCompletionForInaccessiblePhotoGroup() {
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
        given(photoGroupFamilyRepository.findAllByFamilyAndPhotoGroupIds(
                family,
                request.getPhotoGroupIds()
        )).willReturn(List.of());

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

    @Test
    void completesUploadedPhoto() {
        Family family = createFamily();
        PhotoGroup photoGroup = createPhotoGroup();
        User child = createChild(family);
        String idempotencyKey =
                "ee3641b6-7f46-4ca7-a666-17b008f6f486";
        FamilyPhotoUploadCompleteRequest request =
                createCompleteRequest("오늘 찍은 사진");
        FamilyPhoto savedPhoto = FamilyPhoto.builder()
                .familyPhotoId(21L)
                .photoGroup(photoGroup)
                .user(child)
                .imageKey(IMAGE_KEY)
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
        given(persistenceService.create(
                USER_ID,
                IMAGE_KEY,
                idempotencyKey,
                request.getDescription(),
                List.of(photoGroup)
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
        verify(persistenceService).create(
                USER_ID,
                IMAGE_KEY,
                idempotencyKey,
                "오늘 찍은 사진",
                List.of(photoGroup)
        );
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
                idempotencyKey,
                request.getDescription(),
                List.of(photoGroup)
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
                idempotencyKey,
                request.getDescription(),
                List.of(photoGroup)
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
        verify(persistenceService, never()).create(
                USER_ID,
                IMAGE_KEY,
                idempotencyKey,
                request.getDescription(),
                List.of(photoGroup)
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
        request.setPhotoGroupIds(List.of(30L));
        request.setImageKey(IMAGE_KEY);
        request.setDescription(description);
        return request;
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
        PhotoGroupFamily groupFamily = PhotoGroupFamily.builder()
                .family(family)
                .photoGroup(photoGroup)
                .build();

        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(child));
        given(seniorRepository.findById(SENIOR_ID))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(child, family))
                .willReturn(true);
        given(photoGroupFamilyRepository.findAllByFamilyAndPhotoGroupIds(
                family,
                List.of(photoGroup.getId())
        )).willReturn(List.of(groupFamily));
    }
}
