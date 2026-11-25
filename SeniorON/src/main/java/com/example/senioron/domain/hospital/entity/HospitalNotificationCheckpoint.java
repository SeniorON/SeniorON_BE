package com.example.senioron.domain.hospital.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hospital_notification_checkpoint")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HospitalNotificationCheckpoint {

    @Id
    @Column(
            name = "job_name",
            length = 100
    )
    private String jobName;

    @Column(
            name = "last_processed_at",
            nullable = false
    )
    private LocalDateTime lastProcessedAt;

    public void updateLastProcessedAt(
            LocalDateTime lastProcessedAt
    ) {
        this.lastProcessedAt = lastProcessedAt;
    }
}