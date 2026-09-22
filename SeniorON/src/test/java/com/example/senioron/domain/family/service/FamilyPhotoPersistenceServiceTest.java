package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FamilyPhotoPersistenceServiceTest {

    private final FamilyPhotoRepository familyPhotoRepository =
            org.mockito.Mockito.mock(FamilyPhotoRepository.class);
    private final UserRepository userRepository =
            org.mockito.Mockito.mock(UserRepository.class);
    private final FamilyPhotoGroupRepository familyPhotoGroupRepository =
            org.mockito.Mockito.mock(FamilyPhotoGroupRepository.class);

    private FamilyPhotoPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new FamilyPhotoPersistenceService(
                familyPhotoRepository,
                userRepository,
                familyPhotoGroupRepository
        );
    }

    @Test
    void createWithPhotoGroupsSavesAllMappings() {
        User user = User.builder()
                .usersId(2L)
                .name("업로더")
                .build();
        PhotoGroup firstGroup = PhotoGroup.builder()
                .id(3L)
                .name("첫 번째 그룹")
                .build();
        PhotoGroup secondGroup = PhotoGroup.builder()
                .id(4L)
                .name("두 번째 그룹")
                .build();

        given(userRepository.findById(2L))
                .willReturn(Optional.of(user));
        given(familyPhotoRepository.saveAndFlush(any(FamilyPhoto.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        FamilyPhoto savedPhoto = persistenceService.create(
                2L,
                "family-photos/1/multi-group-photo.jpg",
                "multi-group-idempotency-key",
                "여러 그룹 공유",
                List.of(firstGroup, secondGroup)
        );

        assertThat(savedPhoto.getPhotoGroup()).isSameAs(firstGroup);
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
    }
}
