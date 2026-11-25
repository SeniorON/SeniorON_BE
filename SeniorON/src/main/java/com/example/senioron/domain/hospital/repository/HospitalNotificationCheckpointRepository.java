package com.example.senioron.domain.hospital.repository;

import com.example.senioron.domain.hospital.entity.HospitalNotificationCheckpoint;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HospitalNotificationCheckpointRepository
        extends JpaRepository<
        HospitalNotificationCheckpoint,
        String
        > {

    @Modifying(flushAutomatically = true)
    @Query(
            value = """
                    INSERT INTO hospital_notification_checkpoint (
                        job_name,
                        last_processed_at
                    )
                    VALUES (
                        :jobName,
                        :lastProcessedAt
                    )
                    ON DUPLICATE KEY UPDATE
                        job_name = job_name
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("jobName")
            String jobName,

            @Param("lastProcessedAt")
            LocalDateTime lastProcessedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT checkpoint
            FROM HospitalNotificationCheckpoint checkpoint
            WHERE checkpoint.jobName = :jobName
            """)
    Optional<HospitalNotificationCheckpoint>
    findByJobNameForUpdate(
            @Param("jobName")
            String jobName
    );
}