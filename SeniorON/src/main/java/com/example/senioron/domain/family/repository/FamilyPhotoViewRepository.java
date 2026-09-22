package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoView;
import com.example.senioron.domain.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyPhotoViewRepository
        extends JpaRepository<FamilyPhotoView, Long> {

    boolean existsByFamilyPhoto_FamilyPhotoIdAndParent_UsersId(
            Long familyPhotoId,
            Long parentUserId
    );

    default void saveIfAbsent(
            FamilyPhoto familyPhoto,
            User parent
    ) {
        if (existsByFamilyPhoto_FamilyPhotoIdAndParent_UsersId(
                familyPhoto.getFamilyPhotoId(),
                parent.getUsersId()
        )) {
            return;
        }

        save(
                FamilyPhotoView.builder()
                        .familyPhoto(familyPhoto)
                        .parent(parent)
                        .build()
        );
    }

    @Query("""
            SELECT view.familyPhoto.familyPhotoId
            FROM FamilyPhotoView view
            WHERE view.parent.usersId = :parentUserId
              AND view.familyPhoto.familyPhotoId IN :familyPhotoIds
            """)
    List<Long> findViewedFamilyPhotoIds(
            @Param("parentUserId")
            Long parentUserId,

            @Param("familyPhotoIds")
            List<Long> familyPhotoIds
    );
}
