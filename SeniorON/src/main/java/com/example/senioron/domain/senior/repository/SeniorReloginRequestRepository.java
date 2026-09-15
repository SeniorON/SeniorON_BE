package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorReloginRequest;
import com.example.senioron.domain.senior.entity.SeniorReloginRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeniorReloginRequestRepository extends JpaRepository<SeniorReloginRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SeniorReloginRequest> findAllBySeniorAndDeviceAndStatusOrderByCreatedAtDesc(
            Senior senior,
            Device device,
            SeniorReloginRequestStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request
            FROM SeniorReloginRequest request
            JOIN FETCH request.senior senior
            JOIN FETCH senior.family family
            LEFT JOIN FETCH senior.parentUser parentUser
            JOIN FETCH request.device device
            LEFT JOIN FETCH device.user deviceUser
            WHERE request.seniorReloginRequestId = :requestId
            """)
    Optional<SeniorReloginRequest> findByIdForUpdate(
            @Param("requestId") Long requestId
    );

    List<SeniorReloginRequest> findAllBySeniorFamilyAndStatusOrderByCreatedAtDesc(
            Family family,
            SeniorReloginRequestStatus status
    );
}
