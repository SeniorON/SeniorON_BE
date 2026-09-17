package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
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
        WHERE pgf.family = :currentFamily
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
}
