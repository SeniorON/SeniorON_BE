package com.example.senioron.domain.hospital.repository;

import com.example.senioron.domain.hospital.entity.HospitalNotificationCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HospitalNotificationCheckpointRepository
        extends JpaRepository<
        HospitalNotificationCheckpoint,
        String
        > {
}