package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.projection.FamilyPhotoAlbumCountProjection;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FamilyPhotoRepository extends JpaRepository<FamilyPhoto, Long> {

    // 첫 페이지 조회 메서드
    @EntityGraph(attributePaths = {"user", "user.family"})
    List<FamilyPhoto> findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
            Family family,
            Pageable pageable
    );

    // 다음 페이지 조회 메서드
    @EntityGraph(attributePaths = {"user", "user.family"})
    @Query("""
            SELECT fp
            FROM FamilyPhoto fp
            WHERE fp.family = :family
              AND (
                   fp.createdAt < :cursorCreatedAt
                   OR(
                      fp.createdAt = :cursorCreatedAt
                      AND fp.familyPhotoId < :cursorId   
                   )
              )
            ORDER BY fp.createdAt DESC, fp.familyPhotoId DESC
            """)
    List<FamilyPhoto> findNextPageByCursor(
            @Param("family") Family family,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 현재 사용자의 가족에 속한 사진만 조회하여 타 가족 사진 접근 방지
    @EntityGraph(attributePaths = {"user", "user.family"})
    Optional<FamilyPhoto> findByFamilyPhotoIdAndFamily(
            Long familyPhotoId,
            Family family
    );

    @Query("""
        SELECT fp.user
        FROM FamilyPhoto fp
        WHERE fp.family = :family
          AND fp.user.family = :family
          AND NOT EXISTS (
              SELECT newer.familyPhotoId
              FROM FamilyPhoto newer
              WHERE newer.family = :family
                AND newer.user = fp.user
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

    @EntityGraph(attributePaths = {"user"})
    @Query("""
        SELECT fp
        FROM FamilyPhoto fp
        WHERE fp.family = :family
          AND fp.user.family = :family
          AND fp.user.role = :role
          AND NOT EXISTS (
              SELECT newer.familyPhotoId
              FROM FamilyPhoto newer
              WHERE newer.family = :family
                AND newer.user = fp.user
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
                       WHEN fp.viewedByParent = false
                            AND fp.createdAt >= :newPhotoCutoff
                       THEN 1
                       ELSE 0
                   END
               ) AS newPhotoCount
        FROM FamilyPhoto fp
        WHERE fp.family = :family
          AND fp.user.family = :family
          AND fp.user.role = :role
        GROUP BY fp.user.usersId
        """)
    List<FamilyPhotoAlbumCountProjection> countAlbumPhotosByUploader(
            @Param("family") Family family,
            @Param("role") Role role,
            @Param("newPhotoCutoff") LocalDateTime newPhotoCutoff
    );
}
