package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorReloginRequest;
import com.example.senioron.domain.senior.entity.SeniorReloginRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface SeniorReloginRequestRepository extends JpaRepository<SeniorReloginRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SeniorReloginRequest> findAllBySeniorAndDeviceAndStatusOrderByCreatedAtDesc(
            Senior senior,
            Device device,
            SeniorReloginRequestStatus status
    );
}
