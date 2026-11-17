package com.example.senioron.global.storage.repository;

import com.example.senioron.global.storage.entity.S3DeletionTask;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface S3DeletionTaskRepository extends JpaRepository<S3DeletionTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT task
        FROM S3DeletionTask task
        WHERE task.nextAttemptAt <= :now
        ORDER BY task.nextAttemptAt ASC, task.id ASC
        """)
    List<S3DeletionTask> findDueForUpdate(@Param("now") LocalDateTime now, Pageable pageable);
}
