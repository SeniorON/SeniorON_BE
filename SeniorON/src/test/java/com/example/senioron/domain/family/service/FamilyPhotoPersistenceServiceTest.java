package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FamilyPhotoPersistenceServiceTest {

    private final FamilyPhotoRepository familyPhotoRepository =
            org.mockito.Mockito.mock(FamilyPhotoRepository.class);
    private final UserRepository userRepository =
            org.mockito.Mockito.mock(UserRepository.class);
    private final PhotoGroupRepository photoGroupRepository =
            org.mockito.Mockito.mock(PhotoGroupRepository.class);
    private final PhotoGroupFamilyRepository photoGroupFamilyRepository =
            org.mockito.Mockito.mock(PhotoGroupFamilyRepository.class);
    private final FamilyPhotoGroupRepository familyPhotoGroupRepository =
            org.mockito.Mockito.mock(FamilyPhotoGroupRepository.class);

    private FamilyPhotoPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new FamilyPhotoPersistenceService(
                familyPhotoRepository,
                userRepository,
                photoGroupRepository,
                photoGroupFamilyRepository,
                familyPhotoGroupRepository
        );
    }

    @Test
    void createAlsoSavesFamilyPhotoGroupMapping() {
        Family family = Family.builder()
                .familyId(1L)
                .build();
        User user = User.builder()
                .usersId(2L)
                .name("업로더")
                .build();
        user.updateFamily(family);
        PhotoGroup photoGroup = PhotoGroup.builder()
                .id(3L)
                .name("Family 1")
                .build();
        PhotoGroupFamily groupFamily = PhotoGroupFamily.builder()
                .id(4L)
                .family(family)
                .photoGroup(photoGroup)
                .build();

        given(userRepository.findByIdWithFamily(2L))
                .willReturn(Optional.of(user));
        given(photoGroupFamilyRepository.findFirstByFamilyOrderByIdAsc(family))
                .willReturn(Optional.of(groupFamily));
        given(familyPhotoRepository.saveAndFlush(any(FamilyPhoto.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        FamilyPhoto savedPhoto = persistenceService.create(
                2L,
                "family-photos/1/photo.jpg",
                "idempotency-key",
                "설명"
        );

        ArgumentCaptor<FamilyPhotoGroup> captor =
                ArgumentCaptor.forClass(FamilyPhotoGroup.class);
        verify(familyPhotoGroupRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getFamilyPhoto()).isSameAs(savedPhoto);
        assertThat(captor.getValue().getPhotoGroup()).isSameAs(photoGroup);
    }
}
