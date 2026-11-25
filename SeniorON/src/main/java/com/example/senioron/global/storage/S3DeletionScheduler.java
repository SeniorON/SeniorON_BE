package com.example.senioron.global.storage;

import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "storage.s3-deletion", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class S3DeletionScheduler {

    private final S3DeletionService deletionService;
    private final TaskExecutor executor;

    public S3DeletionScheduler(S3DeletionService deletionService,
            @Qualifier("s3DeletionExecutor") TaskExecutor executor) {
        this.deletionService = deletionService;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${storage.s3-deletion.fixed-delay-ms:30000}",
            initialDelayString = "${storage.s3-deletion.initial-delay-ms:30000}")
    public void deletePendingObjects() {
        try {
            // 공용 스케줄러를 S3 네트워크 호출로 막지 않고, 작업이 밀리면 DB에 남겨 둔다.
            executor.execute(this::processBatch);
        } catch (RejectedExecutionException exception) {
            log.debug("S3 삭제 작업자가 사용 중이거나 종료 중. 다음 실행에서 재시도");
        }
    }

    private void processBatch() {
        try {
            int deleted = deletionService.processPendingBatch();
            if (deleted > 0) {
                log.info("S3 삭제 작업 완료. {}건", deleted);
            }
        } catch (RuntimeException exception) {
            // 트랜잭션 실패 시 작업은 DB에 남고 다음 스케줄에서 다시 읽는다.
            log.error("S3 삭제 작업 처리 실패. 다음 실행에서 재시도", exception);
        }
    }
}
