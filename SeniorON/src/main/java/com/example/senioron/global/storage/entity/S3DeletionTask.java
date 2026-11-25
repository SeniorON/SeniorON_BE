package com.example.senioron.global.storage.entity;

import com.example.senioron.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "s3_deletion_task", indexes = @Index(
        name = "idx_s3_deletion_task_due",
        columnList = "next_attempt_at, s3_deletion_task_id"
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class S3DeletionTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "s3_deletion_task_id")
    private Long id;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    public static S3DeletionTask pending(String objectKey, LocalDateTime now) {
        S3DeletionTask task = new S3DeletionTask();
        task.objectKey = objectKey;
        task.nextAttemptAt = now;
        return task;
    }

    public void recordFailure(String error, LocalDateTime now) {
        attemptCount++;
        // 1분부터 지수적으로 늘리고, 영구 실패도 최대 1시간 간격으로 계속 재시도한다.
        long retryDelaySeconds = Math.min(3600L, 60L << Math.min(attemptCount - 1, 6));
        nextAttemptAt = now.plusSeconds(retryDelaySeconds);
        lastError = error == null ? "Unknown S3 deletion error"
                : error.substring(0, Math.min(error.length(), 512));
    }
}
