package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoGroupFamilyRepository
        extends JpaRepository<PhotoGroupFamily, Long> {

    @EntityGraph(attributePaths = "photoGroup")
    Optional<PhotoGroupFamily> findFirstByFamilyOrderByIdAsc(Family family);

    boolean existsByFamilyAndPhotoGroup(Family family, PhotoGroup photoGroup);

    @Query("""
        SELECT pgf
        FROM PhotoGroupFamily pgf
        JOIN FETCH pgf.photoGroup pg
        WHERE pgf.family = :family AND pg.active = true
          AND pg.id IN :photoGroupIds
        ORDER BY pg.id ASC
        """)
    List<PhotoGroupFamily> findAllByFamilyAndPhotoGroupIds(
            @Param("family") Family family,
            @Param("photoGroupIds") List<Long> photoGroupIds
    );

    @Query("""
    SELECT currentLink
    FROM PhotoGroupFamily currentLink
    JOIN FETCH currentLink.photoGroup pg
    WHERE currentLink.family = :family
      AND pg.active = true
      AND pg.id IN :photoGroupIds
      AND NOT EXISTS (
          SELECT targetLink.id
          FROM PhotoGroupFamily targetLink
          WHERE targetLink.photoGroup = pg
            AND NOT EXISTS (
                SELECT member.id
                FROM FamilyMember member
                WHERE member.family = targetLink.family
                  AND member.user = :user
            )
      )
    ORDER BY pg.id ASC
    """)
    List<PhotoGroupFamily> findManagedUploadGroupLinks(
            @Param("family") Family family,
            @Param("user") User user,
            @Param("photoGroupIds") List<Long> photoGroupIds
    );

    @EntityGraph(attributePaths = {
            "photoGroup",
            "family",
            "family.senior"
    })
    @Query("""
    SELECT link
    FROM PhotoGroupFamily link
    WHERE link.photoGroup.active = true
      AND link.family.senior IS NOT NULL
      AND link.id = (
          SELECT MIN(firstLink.id)
          FROM PhotoGroupFamily firstLink
          WHERE firstLink.family = link.family
      )
      AND EXISTS (
          SELECT member.id
          FROM FamilyMember member
          WHERE member.family = link.family
            AND member.user = :user
            AND member.managerType IN :managerTypes
      )
    ORDER BY link.family.familyId ASC
    """)
    List<PhotoGroupFamily> findManagedDefaultPhotoGroupLinks(
            @Param("user") User user,
            @Param("managerTypes") List<ManagerType> managerTypes
    );
}
