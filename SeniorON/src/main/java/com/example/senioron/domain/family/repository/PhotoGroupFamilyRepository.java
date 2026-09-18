package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;

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
        SELECT CASE WHEN COUNT(pgf) > 0 THEN true ELSE false END
        FROM PhotoGroupFamily pgf
        WHERE pgf.family = :currentFamily AND pgf.photoGroup.active = true
          AND EXISTS (
              SELECT other.id
              FROM PhotoGroupFamily other
              WHERE other.photoGroup = pgf.photoGroup
                AND other.family = :targetFamily
          )
        """)
    boolean existsSharedPhotoGroup(
            @Param("currentFamily") Family currentFamily,
            @Param("targetFamily") Family targetFamily
    );

    @EntityGraph(attributePaths = {
            "photoGroup",
            "family",
            "family.senior"
    })
    @Query("""
        SELECT targetLink
        FROM PhotoGroupFamily targetLink
        WHERE targetLink.family <> :currentFamily AND targetLink.photoGroup.active = true
          AND targetLink.photoGroup IN (
              SELECT currentLink.photoGroup
              FROM PhotoGroupFamily currentLink
              WHERE currentLink.family = :currentFamily
          )
        ORDER BY targetLink.id ASC
        """)
    List<PhotoGroupFamily> findConnectedFamilyLinks(
            @Param("currentFamily") Family currentFamily
    );

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

    @EntityGraph(attributePaths = {
            "photoGroup",
            "family"
    })
    @Query("""
    SELECT link
    FROM PhotoGroupFamily link
    WHERE link.photoGroup.id = :photoGroupId AND link.photoGroup.active = true
      AND EXISTS (
          SELECT currentLink.id
          FROM PhotoGroupFamily currentLink
          WHERE currentLink.photoGroup = link.photoGroup
            AND currentLink.family = :currentFamily
      )
    ORDER BY link.id ASC
    """)
    List<PhotoGroupFamily> findAllSharedLinks(
            @Param("currentFamily") Family currentFamily,
            @Param("photoGroupId") Long photoGroupId
    );
}
