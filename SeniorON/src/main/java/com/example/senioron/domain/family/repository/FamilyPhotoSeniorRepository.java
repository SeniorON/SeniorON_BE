package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FamilyPhotoSeniorRepository
        extends JpaRepository<FamilyPhotoSenior, Long> {

    boolean existsBySenior(Senior senior);

    @Query("""
    SELECT fps
    FROM FamilyPhotoSenior fps
    JOIN FETCH fps.senior s
    WHERE fps.familyPhoto = :familyPhoto
    """)
    List<FamilyPhotoSenior> findAllByFamilyPhotoWithSenior(
            @Param("familyPhoto") FamilyPhoto familyPhoto
    );

    // 사용자가 지정한 담당 유형으로 관리하는 시니어에게 공유된 사진 ID
    @Query("""
    SELECT DISTINCT fps.familyPhoto.familyPhotoId
    FROM FamilyPhotoSenior fps
    WHERE fps.familyPhoto.familyPhotoId IN :familyPhotoIds
      AND EXISTS (
          SELECT fm.id
          FROM FamilyMember fm
          WHERE fm.family = fps.senior.family
            AND fm.user = :user
            AND fm.managerType = :managerType
      )
    """)
    Set<Long> findPhotoIdsSharedToSeniorsManagedBy(
            @Param("familyPhotoIds") Collection<Long> familyPhotoIds,
            @Param("user") User user,
            @Param("managerType") ManagerType managerType
    );

    @Query("""
    SELECT DISTINCT parent
    FROM FamilyPhotoSenior fps
    JOIN fps.senior senior
    JOIN senior.parentUser parent
    WHERE fps.familyPhoto.familyPhotoId = :familyPhotoId
      AND senior.seniorId IN :seniorIds
      AND parent.role = :role
      AND parent.status = :status
      AND EXISTS (
          SELECT fm.id
          FROM FamilyMember fm
          WHERE fm.family = senior.family
            AND fm.user = parent
      )
      AND NOT EXISTS (
          SELECT fur.id
          FROM FamilyUserReport fur
          WHERE fur.reporter = parent
            AND fur.reportedUser = fps.familyPhoto.user
            AND fur.hidden = true
      )
    """)
    List<User> findRecipientParents(
            @Param("familyPhotoId") Long familyPhotoId,
            @Param("seniorIds") Collection<Long> seniorIds,
            @Param("role") Role role,
            @Param("status") UserStatus status
    );

    // 신고 화면의 시니어가 이 사진을 공유받았고 신고자가 그 시니어의 가족인지 확인한다.
    @Query("""
    SELECT senior
    FROM FamilyPhotoSenior fps
    JOIN fps.senior senior
    WHERE fps.familyPhoto = :familyPhoto
      AND senior.seniorId = :seniorId
      AND EXISTS (
          SELECT fm.id
          FROM FamilyMember fm
          WHERE fm.family = senior.family
            AND fm.user = :user
      )
    """)
    Optional<Senior> findReportContextSenior(
            @Param("familyPhoto") FamilyPhoto familyPhoto,
            @Param("user") User user,
            @Param("seniorId") Long seniorId
    );

    // seniorId 없이 신고한 경우의 대체 기준: 신고자와 사진이 함께 연결된 시니어 중
    // 신고자가 주담당자인 시니어, 먼저 공유된 시니어 순이다.
    @Query("""
    SELECT senior
    FROM FamilyPhotoSenior fps
    JOIN fps.senior senior
    JOIN FamilyMember fm ON fm.family = senior.family
    WHERE fps.familyPhoto = :familyPhoto
      AND fm.user = :user
    ORDER BY CASE WHEN fm.managerType = :primary THEN 0 ELSE 1 END, fps.id
    """)
    List<Senior> findReportContextSeniors(
            @Param("familyPhoto") FamilyPhoto familyPhoto,
            @Param("user") User user,
            @Param("primary") ManagerType primary,
            Pageable pageable
    );

    @Query("""
    SELECT senior.seniorId
    FROM FamilyPhotoSenior fps
    JOIN fps.senior senior
    WHERE fps.familyPhoto.familyPhotoId = :familyPhotoId
    """)
    List<Long> findSeniorIdsByFamilyPhotoId(
            @Param("familyPhotoId") Long familyPhotoId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
    DELETE FROM FamilyPhotoSenior fps
    WHERE fps.familyPhoto = :familyPhoto
    """)
    void deleteAllByFamilyPhoto(
            @Param("familyPhoto") FamilyPhoto familyPhoto
    );
}
