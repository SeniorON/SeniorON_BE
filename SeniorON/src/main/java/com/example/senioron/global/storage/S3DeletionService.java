package com.example.senioron.global.storage;

import com.example.senioron.global.storage.entity.S3DeletionTask;
import com.example.senioron.global.storage.repository.S3DeletionTaskRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3DeletionService {

    private static final int BATCH_SIZE = 1000;

    private final S3DeletionTaskRepository taskRepository;
    private final S3Service s3Service;

    // 파일 삭제 의도까지 호출자의 DB 변경과 함께 커밋한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Collection<String> objectKeys) {
        LocalDateTime now = LocalDateTime.now();
        List<S3DeletionTask> tasks = objectKeys.stream()
                .filter(Objects::nonNull)
                .filter(key -> !key.isBlank())
                .distinct()
                .map(key -> S3DeletionTask.pending(key, now))
                .toList();
        if (!tasks.isEmpty()) {
            taskRepository.saveAll(tasks);
        }
    }

    // 탈퇴 요청과 분리된 트랜잭션이며 삭제 작업 행만 잠근다.
    // S3 성공 후 DB 커밋 전 종료되어도 작업이 남아, 안전하게 같은 키를 다시 삭제한다.
    // 범위 조회의 gap lock으로 새 탈퇴 요청의 작업 등록까지 막지 않도록 한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public int processPendingBatch() {
        List<S3DeletionTask> tasks = taskRepository.findDueForUpdate(
                LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE)
        );
        if (tasks.isEmpty()) {
            return 0;
        }

        List<String> keys = tasks.stream().map(S3DeletionTask::getObjectKey).distinct().toList();
        Map<String, String> failures;
        try {
            failures = s3Service.deleteObjects(keys);
        } catch (RuntimeException exception) {
            LocalDateTime failedAt = LocalDateTime.now();
            tasks.forEach(task -> task.recordFailure(exception.toString(), failedAt));
            log.error("S3 일괄 삭제 요청 실패. 재시도 대상={}건", tasks.size(), exception);
            return 0;
        }

        LocalDateTime completedAt = LocalDateTime.now();
        List<S3DeletionTask> completed = new ArrayList<>();
        for (S3DeletionTask task : tasks) {
            if (failures.containsKey(task.getObjectKey())) {
                task.recordFailure(failures.get(task.getObjectKey()), completedAt);
                log.warn("S3 객체 삭제 실패. taskId={}, key={}, attempt={}, error={}",
                        task.getId(), task.getObjectKey(), task.getAttemptCount(), task.getLastError());
            } else {
                completed.add(task);
            }
        }
        taskRepository.deleteAllInBatch(completed);
        return completed.size();
    }
}
