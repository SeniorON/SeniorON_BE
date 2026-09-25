package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.senior.dto.response.ManagedSeniorResponse;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    boolean existsByUserAndFamily(User user, Family family);

    Optional<FamilyMember> findByUserAndFamily(User user, Family family);

    @EntityGraph(attributePaths = {"user"})
    List<FamilyMember> findAllByFamilyOrderByIdAsc(Family family);

    long countByFamily(Family family);

    void deleteByUserAndFamily(User user, Family family);

    void deleteAllByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT fm
            FROM FamilyMember fm
            WHERE fm.user = :user
              AND fm.family = :family
            """)
    Optional<FamilyMember> findByUserAndFamilyForUpdate(
            @Param("user") User user,
            @Param("family") Family family
    );

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT fm
            FROM FamilyMember fm
            WHERE fm.family = :family
              AND fm.user.usersId <> :excludeUserId
              AND fm.user.role = :role
            ORDER BY fm.user.usersId ASC
            """)
    List<FamilyMember> findByFamilyAndUserIdNotAndUserRole(
            @Param("family") Family family,
            @Param("excludeUserId") Long excludeUserId,
            @Param("role") Role role
    );

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT fm
            FROM FamilyMember fm
            JOIN fm.family f
            JOIN f.senior s
            WHERE s.seniorId = :seniorId
              AND fm.user.role = :role
            ORDER BY fm.user.usersId ASC
            """)
    List<FamilyMember> findAllBySeniorIdAndUserRole(
            @Param("seniorId") Long seniorId,
            @Param("role") Role role
    );

    List<FamilyMember> findAllByUser(User user);

    @Query("""
            SELECT new com.example.senioron.domain.senior.dto.response.ManagedSeniorResponse(
                f.familyId,
                s.seniorId
            )
            FROM FamilyMember fm
            JOIN fm.family f
            LEFT JOIN f.senior s
            WHERE fm.user.usersId = :userId
            ORDER BY fm.id ASC
            """)
    List<ManagedSeniorResponse> findManagedSeniorResponsesByUserId(
            @Param("userId") Long userId
    );
}
