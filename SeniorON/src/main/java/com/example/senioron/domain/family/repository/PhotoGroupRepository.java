package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.PhotoGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PhotoGroupRepository extends JpaRepository<PhotoGroup, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT pg
        FROM PhotoGroup pg
        WHERE pg.id IN :photoGroupIds
        ORDER BY pg.id ASC
        """)
    List<PhotoGroup> findAllByIdsForUpdate(
            @Param("photoGroupIds") List<Long> photoGroupIds
    );
}