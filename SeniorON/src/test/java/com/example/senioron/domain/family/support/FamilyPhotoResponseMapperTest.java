package com.example.senioron.domain.family.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.storage.S3Service;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class FamilyPhotoResponseMapperTest {

    private static final String IMAGE_KEY = "family-photos/original.jpg";
    private static final String THUMBNAIL_KEY = "family-photos/thumbnails/thumb.jpg";
    private static final String IMAGE_URL = "https://cdn.example.com/original.jpg";
    private static final String THUMBNAIL_URL = "https://cdn.example.com/thumb.jpg";

    private final S3Service s3Service = mock(S3Service.class);
    private final FamilyPhotoResponseMapper mapper = new FamilyPhotoResponseMapper(s3Service);

    private final User parent = User.builder()
            .usersId(1L)
            .name("부모")
            .role(Role.PARENT)
            .build();
    private final User child = User.builder()
            .usersId(2L)
            .name("자녀")
            .role(Role.CHILD)
            .build();

    @BeforeEach
    void setUp() {
        given(s3Service.getFileUrl(IMAGE_KEY)).willReturn(IMAGE_URL);
        given(s3Service.getFileUrl(THUMBNAIL_KEY)).willReturn(THUMBNAIL_URL);
    }

    @Test
    void returnsThumbnailUrlWhenThumbnailKeyExists() {
        FamilyPhoto photo = createPhoto(THUMBNAIL_KEY);

        FamilyPhotoItemResponse response = mapper.toItemResponse(
                photo,
                parent,
                LocalDateTime.now().minusHours(24),
                false,
                true
        );

        assertThat(response.getImageUrl()).isEqualTo(IMAGE_URL);
        assertThat(response.getThumbnailUrl()).isEqualTo(THUMBNAIL_URL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void fallsBackToOriginalUrlWhenThumbnailKeyIsMissing(String thumbnailKey) {
        FamilyPhoto photo = createPhoto(thumbnailKey);

        FamilyPhotoItemResponse response = mapper.toItemResponse(
                photo,
                parent,
                LocalDateTime.now().minusHours(24),
                false,
                true
        );

        assertThat(response.getImageUrl()).isEqualTo(IMAGE_URL);
        assertThat(response.getThumbnailUrl()).isEqualTo(IMAGE_URL);
        verify(s3Service, times(1)).getFileUrl(any());
    }

    @Test
    void keepsCanDeleteAndNewPhotoFromArguments() {
        FamilyPhoto photo = createPhoto(THUMBNAIL_KEY);
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        FamilyPhotoItemResponse unviewed = mapper.toItemResponse(
                photo, parent, cutoff, false, true
        );
        FamilyPhotoItemResponse viewed = mapper.toItemResponse(
                photo, parent, cutoff, true, false
        );
        FamilyPhotoItemResponse byChild = mapper.toItemResponse(
                photo, child, cutoff, false, true
        );

        assertThat(unviewed.isNewPhoto()).isTrue();
        assertThat(unviewed.isCanDelete()).isTrue();
        assertThat(viewed.isNewPhoto()).isFalse();
        assertThat(viewed.isCanDelete()).isFalse();
        assertThat(byChild.isNewPhoto()).isFalse();
        assertThat(unviewed.getFamilyPhotoId()).isEqualTo(30L);
        assertThat(unviewed.getUploaderUserId()).isEqualTo(2L);
        assertThat(unviewed.getUploaderName()).isEqualTo("자녀");
        assertThat(unviewed.getDescription()).isEqualTo("가족 여행");
    }

    private FamilyPhoto createPhoto(String thumbnailKey) {
        FamilyPhoto photo = FamilyPhoto.builder()
                .familyPhotoId(30L)
                .user(child)
                .imageKey(IMAGE_KEY)
                .thumbnailKey(thumbnailKey)
                .description("가족 여행")
                .build();
        ReflectionTestUtils.setField(
                photo,
                "createdAt",
                LocalDateTime.now()
        );
        return photo;
    }
}
