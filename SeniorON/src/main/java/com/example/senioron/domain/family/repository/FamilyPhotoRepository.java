package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.projection.FamilyPhotoAlbumCountProjection;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FamilyPhotoRepository extends JpaRepository<FamilyPhoto, Long> {

    // 첫 페이지 조회 메서드
    @EntityGraph(attributePaths = {"user", "photoGroup"})
    @Query("""
    SELECT fp
    FROM FamilyPhoto fp
    WHERE EXISTS (
        SELECT fpg.id
        FROM FamilyPhotoGroup fpg
        WHERE fpg.familyPhoto = fp
          AND EXISTS (
              SELECT pgf.id
              FROM PhotoGroupFamily pgf
              WHERE pgf.photoGroup = fpg.photoGroup
                AND pgf.family = :family
          )
    )
    ORDER BY fp.createdAt DESC, fp.familyPhotoId DESC
    """)
    List<FamilyPhoto> findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
            @Param("family") Family family,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "photoGroup"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT fp
        FROM FamilyPhoto fp
        WHERE fp.familyPhotoId = :familyPhotoId
          AND EXISTS (
              SELECT fpg.id
              FROM FamilyPhotoGroup fpg
              WHERE fpg.familyPhoto = fp
                AND EXISTS (
                    SELECT pgf.id
                    FROM PhotoGroupFamily pgf
                    WHERE pgf.photoGroup = fpg.photoGroup
                      AND pgf.family = :family
                )
          )
        """)
    Optional<FamilyPhoto> findAccessibleByFamilyPhotoIdAndFamily(
            @Param("familyPhotoId") Long familyPhotoId,
            @Param("family") Family family
    );

    @Query("""
    SELECT fp.user
    FROM FamilyPhoto fp
    WHERE EXISTS (
        SELECT fpg.id
        FROM FamilyPhotoGroup fpg
        WHERE fpg.familyPhoto = fp
          AND EXISTS (
              SELECT pgf.id
              FROM PhotoGroupFamily pgf
              WHERE pgf.photoGroup = fpg.photoGroup
                AND pgf.family = :family
          )
    )
      AND NOT EXISTS (
          SELECT newer.familyPhotoId
          FROM FamilyPhoto newer
          WHERE newer.user = fp.user
            AND EXISTS (
                SELECT newerFpg.id
                FROM FamilyPhotoGroup newerFpg
                WHERE newerFpg.familyPhoto = newer
                  AND EXISTS (
                      SELECT newerPgf.id
                      FROM PhotoGroupFamily newerPgf
                      WHERE newerPgf.photoGroup = newerFpg.photoGroup
                        AND newerPgf.family = :family
                  )
            )
            AND (
                newer.createdAt > fp.createdAt
                OR (
                    newer.createdAt = fp.createdAt
                    AND newer.familyPhotoId > fp.familyPhotoId
                )
            )
      )
    ORDER BY fp.createdAt DESC, fp.familyPhotoId DESC
    """)
    List<User> findRecentUploaders(
            @Param("family") Family family,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "photoGroup"})
    @Query("""
    SELECT fp
    FROM FamilyPhoto fp
    WHERE fp.user.role = :role
      AND EXISTS (
          SELECT fpg.id
          FROM FamilyPhotoGroup fpg
          WHERE fpg.familyPhoto = fp
            AND EXISTS (
                SELECT pgf.id
                FROM PhotoGroupFamily pgf
                WHERE pgf.photoGroup = fpg.photoGroup
                  AND pgf.family = :family
            )
      )
      AND NOT EXISTS (
          SELECT newer.familyPhotoId
          FROM FamilyPhoto newer
          WHERE newer.user = fp.user
            AND EXISTS (
                SELECT newerFpg.id
                FROM FamilyPhotoGroup newerFpg
                WHERE newerFpg.familyPhoto = newer
                  AND EXISTS (
                      SELECT newerPgf.id
                      FROM PhotoGroupFamily newerPgf
                      WHERE newerPgf.photoGroup = newerFpg.photoGroup
                        AND newerPgf.family = :family
                  )
            )
            AND (
                newer.createdAt > fp.createdAt
                OR (
                    newer.createdAt = fp.createdAt
                    AND newer.familyPhotoId > fp.familyPhotoId
                )
            )
      )
    ORDER BY fp.createdAt DESC, fp.familyPhotoId DESC
    """)
    List<FamilyPhoto> findLatestPhotosByUploader(
            @Param("family") Family family,
            @Param("role") Role role
    );

    @Query("""
        SELECT fp.user.usersId AS uploaderUserId,
               COUNT(fp.familyPhotoId) AS photoCount,
               SUM(
                   CASE
                       WHEN fp.createdAt >= :newPhotoCutoff
                            AND NOT EXISTS (
                                SELECT fpv.id
                                FROM FamilyPhotoView fpv
                                WHERE fpv.familyPhoto = fp
                                  AND fpv.parent = :parent
                            )
                       THEN 1
                       ELSE 0
                   END
               ) AS newPhotoCount
        FROM FamilyPhoto fp
        WHERE fp.user.role = :role
          AND EXISTS (
              SELECT fpg.id
              FROM FamilyPhotoGroup fpg
              WHERE fpg.familyPhoto = fp
                AND EXISTS (
                    SELECT pgf.id
                    FROM PhotoGroupFamily pgf
                    WHERE pgf.photoGroup = fpg.photoGroup
                      AND pgf.family = :family
                )
          )
        GROUP BY fp.user.usersId
        """)
    List<FamilyPhotoAlbumCountProjection> countAlbumPhotosByUploader(
            @Param("family")
            Family family,

            @Param("role")
            Role role,

            @Param("newPhotoCutoff")
            LocalDateTime newPhotoCutoff,

            @Param("parent")
            User parent
    );

    @EntityGraph(attributePaths = {"photoGroup", "user"})
    Optional<FamilyPhoto> findByUserUsersIdAndIdempotencyKey(
            Long usersId,
            String idempotencyKey
    );

    boolean existsByImageKey(String imageKey);

    @EntityGraph(attributePaths = {"user", "photoGroup"})
    @Query("""
        SELECT fp
        FROM FamilyPhoto fp
        WHERE fp.familyPhotoId = :familyPhotoId
          AND EXISTS (
              SELECT fpg.id
              FROM FamilyPhotoGroup fpg
              WHERE fpg.familyPhoto = fp
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
          )
        """)
    Optional<FamilyPhoto> findAccessibleByFamilyPhotoIdAndUser(
            @Param("familyPhotoId") Long familyPhotoId,
            @Param("user") User user
    );

    @EntityGraph(attributePaths = {"user", "photoGroup"})
    @Query("""
        SELECT fp
        FROM FamilyPhoto fp
        WHERE EXISTS (
            SELECT fpg.id
            FROM FamilyPhotoGroup fpg
            WHERE fpg.familyPhoto = fp
              AND EXISTS (
                  SELECT pgf.id
                  FROM PhotoGroupFamily pgf
                  WHERE pgf.photoGroup = fpg.photoGroup
                    AND pgf.family = :family
              )
        )
        ORDER BY fp.createdAt DESC, fp.familyPhotoId DESC
        """)
    List<FamilyPhoto> findAllAccessibleByFamily(
            @Param("family") Family family,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "photoGroup"})
    @Query("""
        SELECT fp
        FROM FamilyPhoto fp
        WHERE EXISTS (
            SELECT fpg.id
            FROM FamilyPhotoGroup fpg
            WHERE fpg.familyPhoto = fp
              AND EXISTS (
                  SELECT pgf.id
                  FROM PhotoGroupFamily pgf
                  WHERE pgf.photoGroup = fpg.photoGroup
                    AND pgf.family = :family
              )
        )
          AND (
              fp.createdAt < :cursorCreatedAt
              OR (
                  fp.createdAt = :cursorCreatedAt
                  AND fp.familyPhotoId < :cursorId
              )
          )
        ORDER BY fp.createdAt DESC, fp.familyPhotoId DESC
        """)
    List<FamilyPhoto> findNextAccessiblePageByFamily(
            @Param("family") Family family,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "photoGroup"})
    @Query("""
        SELECT fp
        FROM FamilyPhoto fp
        WHERE fp.user = :uploader
          AND EXISTS (
              SELECT fpg.id
              FROM FamilyPhotoGroup fpg
              WHERE fpg.familyPhoto = fp
                AND EXISTS (
                    SELECT pgf.id
                    FROM PhotoGroupFamily pgf
                    WHERE pgf.photoGroup = fpg.photoGroup
                      AND pgf.family = :family
                )
          )
        ORDER BY fp.createdAt DESC, fp.familyPhotoId DESC
        """)
    List<FamilyPhoto> findAllAccessibleByFamilyAndUploader(
            @Param("family") Family family,
            @Param("uploader") User uploader,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "photoGroup"})
    @Query("""
        SELECT fp
        FROM FamilyPhoto fp
        WHERE fp.user = :uploader
          AND EXISTS (
              SELECT fpg.id
              FROM FamilyPhotoGroup fpg
              WHERE fpg.familyPhoto = fp
                AND EXISTS (
                    SELECT pgf.id
                    FROM PhotoGroupFamily pgf
                    WHERE pgf.photoGroup = fpg.photoGroup
                      AND pgf.family = :family
                )
          )
          AND (
              fp.createdAt < :cursorCreatedAt
              OR (
                  fp.createdAt = :cursorCreatedAt
                  AND fp.familyPhotoId < :cursorId
              )
          )
        ORDER BY fp.createdAt DESC, fp.familyPhotoId DESC
        """)
    List<FamilyPhoto> findNextAccessiblePageByFamilyAndUploader(
            @Param("family") Family family,
            @Param("uploader") User uploader,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(fp)
        FROM FamilyPhoto fp
        WHERE EXISTS (
            SELECT fpg.id
            FROM FamilyPhotoGroup fpg
            WHERE fpg.familyPhoto = fp
              AND EXISTS (
                  SELECT pgf.id
                  FROM PhotoGroupFamily pgf
                  WHERE pgf.photoGroup = fpg.photoGroup
                    AND pgf.family = :family
              )
        )
        """)
    long countAccessibleByFamily(
            @Param("family") Family family
    );

    @Query("""
        SELECT COUNT(fp)
        FROM FamilyPhoto fp
        WHERE fp.user = :uploader
          AND EXISTS (
              SELECT fpg.id
              FROM FamilyPhotoGroup fpg
              WHERE fpg.familyPhoto = fp
                AND EXISTS (
                    SELECT pgf.id
                    FROM PhotoGroupFamily pgf
                    WHERE pgf.photoGroup = fpg.photoGroup
                      AND pgf.family = :family
                )
          )
        """)
    long countAccessibleByFamilyAndUploader(
            @Param("family") Family family,
            @Param("uploader") User uploader
    );
}
