package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.event.FamilyPhotoDeletedEvent;
import com.example.senioron.domain.family.repository.FamilyPhotoReportRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoViewRepository;
import com.example.senioron.domain.family.repository.FamilyUserReportRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.storage.S3DeletionService;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class FamilyPhotoWithdrawalServiceTest {

    private final FamilyPhotoRepository photos = mock(FamilyPhotoRepository.class);
    private final FamilyPhotoSeniorRepository shares = mock(FamilyPhotoSeniorRepository.class);
    private final FamilyPhotoViewRepository views = mock(FamilyPhotoViewRepository.class);
    private final FamilyPhotoReportRepository photoReports = mock(FamilyPhotoReportRepository.class);
    private final FamilyUserReportRepository userReports = mock(FamilyUserReportRepository.class);
    private final FamilyPhotoPushTargetService pushTargets = mock(FamilyPhotoPushTargetService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final S3DeletionService deletions = mock(S3DeletionService.class);
    private final User user = User.builder().usersId(1L).build();
    private final FamilyPhotoWithdrawalService service = new FamilyPhotoWithdrawalService(
            photos, shares, views, photoReports, userReports, pushTargets, events, deletions
    );

    @Test
    void capturesRecipientsBeforeDeletingSharesAndQueuesFiles() {
        FamilyPhoto photo = FamilyPhoto.builder()
                .familyPhotoId(10L).user(user).imageKey("original.jpg")
                .thumbnailKey("thumbnail.jpg").build();
        given(photos.findAllByUploaderForUpdate(user)).willReturn(List.of(photo));
        given(shares.findSeniorIdsByFamilyPhotoId(10L)).willReturn(List.of(20L, 30L));
        given(pushTargets.loadDeletionTokens(10L, List.of(20L, 30L)))
                .willReturn(List.of("parent-token"));

        service.deleteUploadedPhotos(user);

        var order = inOrder(pushTargets, views, shares, photos);
        order.verify(pushTargets).loadDeletionTokens(10L, List.of(20L, 30L));
        order.verify(views).deleteAllByFamilyPhoto(photo);
        order.verify(shares).deleteAllByFamilyPhoto(photo);
        order.verify(photos).delete(photo);
        ArgumentCaptor<FamilyPhotoDeletedEvent> event =
                ArgumentCaptor.forClass(FamilyPhotoDeletedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().familyPhotoId()).isEqualTo(10L);
        assertThat(event.getValue().tokens()).containsExactly("parent-token");
        verify(deletions).enqueue(Set.of("original.jpg", "thumbnail.jpg"));
    }

    @Test
    void queuesRetainedReportOriginalsWithoutPhotosAndDeduplicatesKeys() {
        given(photoReports.findImageKeysByReportedUser(user))
                .willReturn(List.of("retained.jpg", "retained.jpg"));
        given(userReports.findImageKeysByReportedUser(user))
                .willReturn(List.of("retained.jpg", "other.jpg"));

        service.deleteUploadedPhotos(user);
        verify(deletions).enqueue(Set.of("retained.jpg", "other.jpg"));
        verifyNoInteractions(pushTargets, events);
    }

    @Test
    void passesNullAndBlankThumbnailKeysToQueueForNormalization() {
        given(photos.findAllByUploaderForUpdate(user)).willReturn(List.of(
                FamilyPhoto.builder().familyPhotoId(10L).imageKey("first.jpg").build(),
                FamilyPhoto.builder().familyPhotoId(11L).imageKey("next.jpg")
                        .thumbnailKey(" ").build()
        ));
        service.deleteUploadedPhotos(user);
        verify(deletions).enqueue(new LinkedHashSet<>(
                Arrays.asList("first.jpg", null, "next.jpg", " ")
        ));
    }

    @Test
    void queuesNothingWhenThereAreNoPhotosOrRetainedOriginals() {
        service.deleteUploadedPhotos(user);

        verifyNoInteractions(deletions, pushTargets, events);
    }
}
