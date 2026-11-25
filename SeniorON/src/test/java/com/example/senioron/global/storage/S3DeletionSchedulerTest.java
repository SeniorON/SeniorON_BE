package com.example.senioron.global.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class S3DeletionSchedulerTest {

    private final S3DeletionService service = mock(S3DeletionService.class);
    private final TaskExecutor executor = mock(TaskExecutor.class);
    private final S3DeletionScheduler scheduler = new S3DeletionScheduler(service, executor);

    @Test
    void dispatchesToDedicatedExecutorWithoutBlockingTheScheduler() {
        scheduler.deletePendingObjects();

        verifyNoInteractions(service);
        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(work.capture());
        work.getValue().run();
        verify(service).processPendingBatch();
    }

    @Test
    void leavesWorkInDatabaseWhenExecutorRejectsDispatch() {
        doThrow(new TaskRejectedException("Worker busy"))
                .when(executor).execute(any(Runnable.class));

        assertThatCode(scheduler::deletePendingObjects).doesNotThrowAnyException();
        verifyNoInteractions(service);
    }

    @Test
    void containsTransactionFailureForTheNextScheduledRetry() {
        doThrow(new IllegalStateException("DB unavailable")).when(service).processPendingBatch();
        scheduler.deletePendingObjects();
        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(work.capture());

        assertThatCode(() -> work.getValue().run()).doesNotThrowAnyException();
    }
}
