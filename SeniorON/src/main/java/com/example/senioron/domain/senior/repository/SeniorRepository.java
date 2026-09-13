package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeniorRepository extends JpaRepository<Senior, Long> {

    List<Senior> findAllByRegisteredBy(User registeredBy);

    Optional<Senior> findFirstByRegisteredBy(User registeredBy);

    Optional<Senior> findFirstByFamilyOrderBySeniorIdAsc(Family family);

    List<Senior> findAllByFamilyOrderBySeniorIdAsc(Family family);

    boolean existsByParentUser(User parentUser);

    Optional<Senior> findByParentUser(User parentUser);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Senior s LEFT JOIN FETCH s.parentUser WHERE s.seniorId = :seniorId")
    Optional<Senior> findByIdForUpdate(@Param("seniorId") Long seniorId);
}
