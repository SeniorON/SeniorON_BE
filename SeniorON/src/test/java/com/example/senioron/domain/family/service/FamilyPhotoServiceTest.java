package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoViewRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class FamilyPhotoServiceTest {

    private final FamilyPhotoRepository familyPhotoRepository =
            org.mockito.Mockito.mock(FamilyPhotoRepository.class);
    private final FamilyPhotoViewRepository familyPhotoViewRepository =
            org.mockito.Mockito.mock(FamilyPhotoViewRepository.class);
    private final UserRepository userRepository =
            org.mockito.Mockito.mock(UserRepository.class);
    private final S3Service s3Service =
            org.mockito.Mockito.mock(S3Service.class);
    private final FamilyPhotoPermissionService familyPhotoPermissionService =
            org.mockito.Mockito.mock(FamilyPhotoPermissionService.class);
    private final FamilyPhotoPersistenceService photoPersistenceService =
            org.mockito.Mockito.mock(FamilyPhotoPersistenceService.class);
    private final FamilyMemberRepository familyMemberRepository =
            org.mockito.Mockito.mock(FamilyMemberRepository.class);
    private final SeniorRepository seniorRepository =
            org.mockito.Mockito.mock(SeniorRepository.class);
    private final PhotoGroupFamilyRepository photoGroupFamilyRepository =
            org.mockito.Mockito.mock(PhotoGroupFamilyRepository.class);

    private FamilyPhotoService familyPhotoService;

    @BeforeEach
    void setUp() {
        familyPhotoService = new FamilyPhotoService(
                familyPhotoRepository,
                familyPhotoViewRepository,
                userRepository,
                s3Service,
                familyPhotoPermissionService,
                photoPersistenceService,
                familyMemberRepository,
                seniorRepository,
                photoGroupFamilyRepository
        );
    }

    @Test
    void markPhotoAsViewedUsesSelectedFamilyAndStoresParentSpecificView() {
        Family firstFamily = Family.builder()
                .familyId(1L)
                .build();

        Family selectedFamily = Family.builder()
                .familyId(2L)
                .build();

        User parent = User.builder()
                .usersId(10L)
                .role(Role.PARENT)
                .build();

        // 과거 코드가 선택하던 첫 번째 Family를 의도적으로 설정한다.
        parent.updateFamily(firstFamily);

        Senior selectedSenior = Senior.builder()
                .seniorId(20L)
                .family(selectedFamily)
                .build();

        FamilyPhoto photo = FamilyPhoto.builder()
                .familyPhotoId(30L)
                .build();

        given(userRepository.findById(10L))
                .willReturn(Optional.of(parent));
        given(seniorRepository.findById(20L))
                .willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(
                parent,
                selectedFamily
        )).willReturn(true);
        given(familyPhotoRepository
                .findAccessibleByFamilyPhotoIdAndFamily(
                        30L,
                        selectedFamily
                ))
                .willReturn(Optional.of(photo));

        familyPhotoService.markPhotoAsViewed(
                parent,
                20L,
                30L
        );

        verify(familyPhotoRepository)
                .findAccessibleByFamilyPhotoIdAndFamily(
                        30L,
                        selectedFamily
                );
        verify(familyPhotoRepository, never())
                .findAccessibleByFamilyPhotoIdAndFamily(
                        30L,
                        firstFamily
                );
        verify(familyPhotoViewRepository)
                .saveIfAbsent(
                        photo,
                        parent
                );
    }

    @Test
    void markPhotoAsViewedRejectsParentWithoutAccessToSelectedSenior() {
        Family selectedFamily = Family.builder()
                .familyId(2L)
                .build();

        User parent = User.builder()
                .usersId(10L)
                .role(Role.PARENT)
                .build();

        Senior selectedSenior = Senior.builder()
                .seniorId(20L)
                .family(selectedFamily)
                .build();

        given(userRepository.findById(10L))
                .willReturn(Optional.of(parent));
        given(seniorRepository.findById(20L))
                .willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(
                parent,
                selectedFamily
        )).willReturn(false);

        assertThatThrownBy(() ->
                familyPhotoService.markPhotoAsViewed(
                        parent,
                        20L,
                        30L
                )
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(
                        ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED
                );

        verify(familyPhotoRepository, never())
                .findAccessibleByFamilyPhotoIdAndFamily(
                        any(),
                        any()
                );
        verify(familyPhotoViewRepository, never())
                .saveIfAbsent(
                        any(),
                        any()
                );
    }

    @Test
    void markPhotoAsViewedRejectsPhotoOutsideSelectedFamilyPhotoGroups() {
        Family selectedFamily = Family.builder()
                .familyId(2L)
                .build();

        User parent = User.builder()
                .usersId(10L)
                .role(Role.PARENT)
                .build();

        Senior selectedSenior = Senior.builder()
                .seniorId(20L)
                .family(selectedFamily)
                .build();

        given(userRepository.findById(10L))
                .willReturn(Optional.of(parent));
        given(seniorRepository.findById(20L))
                .willReturn(Optional.of(selectedSenior));
        given(familyMemberRepository.existsByUserAndFamily(
                parent,
                selectedFamily
        )).willReturn(true);
        given(familyPhotoRepository
                .findAccessibleByFamilyPhotoIdAndFamily(
                        30L,
                        selectedFamily
                ))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                familyPhotoService.markPhotoAsViewed(
                        parent,
                        20L,
                        30L
                )
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_NOT_FOUND);

        verify(familyPhotoViewRepository, never())
                .saveIfAbsent(
                        any(),
                        any()
                );
    }

    @Test
    void getPhotosCalculatesNewPhotoForCurrentParent() {
        Family family = Family.builder()
                .familyId(1L)
                .build();
        User parent = User.builder()
                .usersId(10L)
                .role(Role.PARENT)
                .build();
        User uploader = User.builder()
                .usersId(20L)
                .name("자녀")
                .role(Role.CHILD)
                .build();
        Senior senior = Senior.builder()
                .seniorId(30L)
                .family(family)
                .build();
        FamilyPhoto photo = FamilyPhoto.builder()
                .familyPhotoId(40L)
                .user(uploader)
                .imageKey("photo.jpg")
                .build();
        ReflectionTestUtils.setField(
                photo,
                "createdAt",
                LocalDateTime.now()
        );

        given(userRepository.findById(10L))
                .willReturn(Optional.of(parent));
        given(seniorRepository.findById(30L))
                .willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(parent, family))
                .willReturn(true);
        given(familyPhotoRepository.findAllAccessibleByFamily(
                family,
                PageRequest.of(0, 11)
        )).willReturn(List.of(photo));
        given(familyPhotoRepository.countAccessibleByFamily(family))
                .willReturn(1L);
        given(familyPhotoViewRepository.findViewedFamilyPhotoIds(
                10L,
                List.of(40L)
        )).willReturn(List.of(), List.of(40L));

        var beforeViewed = familyPhotoService.getPhotos(
                parent,
                30L,
                null,
                null,
                null,
                10
        );
        var afterViewed = familyPhotoService.getPhotos(
                parent,
                30L,
                null,
                null,
                null,
                10
        );

        assertThat(beforeViewed.getPhotos())
                .singleElement()
                .satisfies(item -> assertThat(item.isNewPhoto()).isTrue());
        assertThat(afterViewed.getPhotos())
                .singleElement()
                .satisfies(item -> assertThat(item.isNewPhoto()).isFalse());
    }

    @Test
    void getPhotoCalculatesNewPhotoForCurrentParent() {
        User parent = User.builder()
                .usersId(10L)
                .role(Role.PARENT)
                .build();
        User uploader = User.builder()
                .usersId(20L)
                .name("자녀")
                .role(Role.CHILD)
                .build();
        FamilyPhoto photo = FamilyPhoto.builder()
                .familyPhotoId(40L)
                .user(uploader)
                .imageKey("photo.jpg")
                .build();
        ReflectionTestUtils.setField(
                photo,
                "createdAt",
                LocalDateTime.now()
        );

        given(userRepository.findById(10L))
                .willReturn(Optional.of(parent));
        given(familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(
                40L,
                parent
        )).willReturn(Optional.of(photo));
        given(familyPhotoViewRepository
                .existsByFamilyPhoto_FamilyPhotoIdAndParent_UsersId(
                        40L,
                        10L
                ))
                .willReturn(false, true);

        var beforeViewed = familyPhotoService.getPhoto(parent, 40L);
        var afterViewed = familyPhotoService.getPhoto(parent, 40L);

        assertThat(beforeViewed.isNewPhoto()).isTrue();
        assertThat(afterViewed.isNewPhoto()).isFalse();
    }
}
