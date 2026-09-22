package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyPhotoGroupRepository
        extends JpaRepository<FamilyPhotoGroup, Long> {

    @Query("""
    SELECT CASE WHEN COUNT(fpg) > 0 THEN true ELSE false END
    FROM FamilyPhotoGroup fpg
    WHERE fpg.familyPhoto = :familyPhoto
      AND EXISTS (
          SELECT pgf.id
          FROM PhotoGroupFamily pgf
          WHERE pgf.photoGroup = fpg.photoGroup
            AND EXISTS (
                SELECT fm.id
                FROM FamilyMember fm
                WHERE fm.family = pgf.family
                  AND fm.user = :user
            )
      )
    """)
    boolean existsAccessibleByFamilyPhotoAndUser(
            @Param("familyPhoto") FamilyPhoto familyPhoto,
            @Param("user") User user
    );

    @Query("""
    SELECT CASE WHEN COUNT(fpg) > 0 THEN true ELSE false END
    FROM FamilyPhotoGroup fpg
    WHERE fpg.familyPhoto = :familyPhoto
      AND EXISTS (
          SELECT pgf.id
          FROM PhotoGroupFamily pgf
          WHERE pgf.photoGroup = fpg.photoGroup
            AND EXISTS (
                SELECT fm.id
                FROM FamilyMember fm
                WHERE fm.family = pgf.family
                  AND fm.user = :user
                  AND fm.managerType = :managerType
            )
      )
    """)
    boolean existsAccessibleByFamilyPhotoAndUserAndManagerType(
            @Param("familyPhoto") FamilyPhoto familyPhoto,
            @Param("user") User user,
            @Param("managerType") ManagerType managerType
    );
}