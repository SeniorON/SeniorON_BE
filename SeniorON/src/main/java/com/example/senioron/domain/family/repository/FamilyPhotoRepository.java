package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FamilyPhotoRepository extends JpaRepository<FamilyPhoto, Long> {

    // 첫 페이지: 커서 없이 최신순으로 조회
    @EntityGraph(attributePaths = "user")
    List<FamilyPhoto> findByFamilyOrderByFamilyPhotoIdDesc(
            Family family,
            Pageable pageable
    );

    // 다음 페이지: 커서보다 ID가 작은 사진을 최신순으로 조회
    @EntityGraph(attributePaths = "user")
    List<FamilyPhoto> findByFamilyAndFamilyPhotoIdLessThanOrderByFamilyPhotoIdDesc(
            Family family,
            Long cursor,
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
            WHERE fp.family=:family
            AND fp.user.family=:family
            GROUP BY fp.user
            ORDER BY MAX(fp.familyPhotoId) DESC
            """)
    List<User> findRecentUploaders(
            @Param("family") Family family,
            Pageable pageable
    );
}
