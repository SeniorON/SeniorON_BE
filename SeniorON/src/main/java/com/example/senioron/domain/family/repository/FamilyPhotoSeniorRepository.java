package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface FamilyPhotoSeniorRepository
        extends JpaRepository<FamilyPhotoSenior, Long> {

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
    """)
    List<User> findRecipientParents(
            @Param("familyPhotoId") Long familyPhotoId,
            @Param("seniorIds") Collection<Long> seniorIds,
            @Param("role") Role role,
            @Param("status") UserStatus status
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
