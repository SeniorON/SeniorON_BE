package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.global.storage.S3Service;
import com.example.senioron.global.storage.ThumbnailService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FamilyPhotoThumbnailBackfillServiceTest {

    private final FamilyPhotoRepository repository = mock(FamilyPhotoRepository.class);
    private final FamilyPhotoPersistenceService persistenceService =
            mock(FamilyPhotoPersistenceService.class);
    private final S3Service s3Service = mock(S3Service.class);
    private final ThumbnailService thumbnailService = mock(ThumbnailService.class);
    private final FamilyPhotoThumbnailBackfillService service =
            new FamilyPhotoThumbnailBackfillService(
                    repository, persistenceService, s3Service, thumbnailService
            );

    @Test
    void generatesThumbnailsAcrossBatchesAndAdvancesCursor() {
        FamilyPhoto first = photo(1L);
        FamilyPhoto second = photo(2L);
        FamilyPhoto third = photo(3L);
        given(repository.findPhotosWithoutThumbnail(0L, PageRequest.of(0, 100)))
                .willReturn(List.of(first, second));
        given(repository.findPhotosWithoutThumbnail(2L, PageRequest.of(0, 100)))
                .willReturn(List.of(third));
        given(repository.findPhotosWithoutThumbnail(3L, PageRequest.of(0, 100)))
                .willReturn(List.of());
        stubSuccessfulGeneration(first);
        stubSuccessfulGeneration(second);
        stubSuccessfulGeneration(third);

        assertThat(service.backfill()).isEqualTo(3);

        verify(repository).findPhotosWithoutThumbnail(2L, PageRequest.of(0, 100));
        verify(repository).findPhotosWithoutThumbnail(3L, PageRequest.of(0, 100));
        for (FamilyPhoto photo : List.of(first, second, third)) {
            verify(persistenceService).attachThumbnail(
                    photo.getFamilyPhotoId(), thumbnailKey(photo)
            );
        }
        verify(s3Service, never()).delete(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "download", "generate", "upload", "attachRejected", "attachFailed", "cleanupFailed"
    })
    void continuesAfterFailureAndCleansOnlyUnattachedThumbnail(String failureStage) {
        FamilyPhoto failedPhoto = photo(1L);
        FamilyPhoto nextPhoto = photo(2L);
        given(repository.findPhotosWithoutThumbnail(0L, PageRequest.of(0, 100)))
                .willReturn(List.of(failedPhoto, nextPhoto));
        given(repository.findPhotosWithoutThumbnail(2L, PageRequest.of(0, 100)))
                .willReturn(List.of());
        byte[][] failedBytes = stubSuccessfulGeneration(failedPhoto);
        stubSuccessfulGeneration(nextPhoto);
        RuntimeException failure = new IllegalStateException("backfill stage failed");
        switch (failureStage) {
            case "download" -> given(s3Service.download(failedPhoto.getImageKey()))
                    .willThrow(failure);
            case "generate" -> given(thumbnailService.create(failedBytes[0]))
                    .willThrow(failure);
            case "upload" -> given(s3Service.uploadThumbnail(failedBytes[1]))
                    .willThrow(failure);
            case "attachFailed" -> given(persistenceService.attachThumbnail(
                    1L, thumbnailKey(failedPhoto)
            )).willThrow(failure);
            default -> given(persistenceService.attachThumbnail(
                    1L, thumbnailKey(failedPhoto)
            )).willReturn(false);
        }
        if (failureStage.equals("cleanupFailed")) {
            doThrow(failure).when(s3Service).delete(thumbnailKey(failedPhoto));
        }

        assertThat(service.backfill()).isEqualTo(1);

        verify(persistenceService).attachThumbnail(2L, thumbnailKey(nextPhoto));
        verify(repository).findPhotosWithoutThumbnail(2L, PageRequest.of(0, 100));
        verify(s3Service, never()).delete(failedPhoto.getImageKey());
        verify(s3Service, never()).delete(nextPhoto.getImageKey());
        verify(s3Service, never()).delete(thumbnailKey(nextPhoto));
        if (failureStage.startsWith("attach") || failureStage.equals("cleanupFailed")) {
            verify(s3Service).delete(thumbnailKey(failedPhoto));
        } else {
            verify(s3Service, never()).delete(anyString());
        }
    }

    @Test
    void doesNothingWhenNoPhotosAreMissingThumbnails() {
        given(repository.findPhotosWithoutThumbnail(0L, PageRequest.of(0, 100)))
                .willReturn(List.of());

        assertThat(service.backfill()).isZero();

        verifyNoInteractions(s3Service, thumbnailService, persistenceService);
    }

    private FamilyPhoto photo(Long id) {
        return FamilyPhoto.builder()
                .familyPhotoId(id)
                .imageKey("original/" + id + ".jpg")
                .build();
    }

    private String thumbnailKey(FamilyPhoto photo) {
        return "thumbnails/" + photo.getFamilyPhotoId() + ".jpg";
    }

    private byte[][] stubSuccessfulGeneration(FamilyPhoto photo) {
        byte[] original = {photo.getFamilyPhotoId().byteValue()};
        byte[] thumbnail = {photo.getFamilyPhotoId().byteValue(), 2};
        given(s3Service.download(photo.getImageKey())).willReturn(original);
        given(thumbnailService.create(original)).willReturn(thumbnail);
        given(s3Service.uploadThumbnail(thumbnail)).willReturn(thumbnailKey(photo));
        given(persistenceService.attachThumbnail(
                photo.getFamilyPhotoId(), thumbnailKey(photo)
        )).willReturn(true);
        return new byte[][]{original, thumbnail};
    }
}
