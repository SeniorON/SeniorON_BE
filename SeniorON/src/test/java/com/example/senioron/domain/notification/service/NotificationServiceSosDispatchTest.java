package com.example.senioron.domain.notification.service;

import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.notification.dto.NotificationDispatchTarget;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import com.example.senioron.domain.notification.repository.NotificationSettingRepository;
import com.example.senioron.domain.user.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Timeout(15)
class NotificationServiceSosDispatchTest {

    private final FcmSender fcmSender = mock(FcmSender.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final NotificationService service = new NotificationService(
            mock(NotificationRepository.class), mock(NotificationSettingRepository.class),
            mock(UserRepository.class), mock(DeviceRepository.class), fcmSender, meterRegistry);
    private final ExecutorService requestExecutor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() throws InterruptedException {
        service.shutdownDispatchExecutors();
        requestExecutor.shutdownNow();
        assertThat(requestExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        meterRegistry.close();
    }

    @Test
    void returnsImmediatelyWithoutWaitingForFcm() throws Exception {
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        given(fcmSender.sendHighPriority("slow", "SOS", "도움이 필요해요", 100L)).willAnswer(invocation -> {
            sendStarted.countDown();
            await(releaseSend);
            return true;
        });

        try {
            var response = requestExecutor.submit(() -> service.dispatchSosAsync(List.of(target(1L, "slow"))));

            response.get(500, TimeUnit.MILLISECONDS);
            assertThat(sendStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(counterOrZero("delivered")).isZero();

            releaseSend.countDown();
            awaitDispatchCount("delivered", 1.0, 2);
        } finally {
            releaseSend.countDown();
        }
    }

    @Test
    void firstSuccessfulDeviceCompletesReceiverWhileSlowDeviceContinues() throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        given(fcmSender.sendHighPriority("slow", "SOS", "도움이 필요해요", 100L)).willAnswer(invocation -> {
            slowStarted.countDown();
            await(releaseSlow);
            return true;
        });
        given(fcmSender.sendHighPriority("fast", "SOS", "도움이 필요해요", 100L)).willAnswer(invocation -> {
            await(slowStarted);
            return true;
        });

        try {
            service.dispatchSosAsync(List.of(target(1L, "slow", "fast")));

            awaitDispatchCount("delivered", 1.0, 2);
            releaseSlow.countDown();
            verify(fcmSender).sendHighPriority("slow", "SOS", "도움이 필요해요", 100L);
            verify(fcmSender).sendHighPriority("fast", "SOS", "도움이 필요해요", 100L);
        } finally {
            releaseSlow.countDown();
        }
    }

    @Test
    void recordsPartialResultByReceiverAfterAllReceiversComplete() throws Exception {
        CountDownLatch releaseSecondReceiver = new CountDownLatch(1);
        given(fcmSender.sendHighPriority(anyString(), anyString(), anyString(), anyLong())).willAnswer(invocation -> {
            if ("second-receiver".equals(invocation.getArgument(0))) {
                await(releaseSecondReceiver);
            }
            return true;
        });

        try {
            service.dispatchSosAsync(List.of(
                    target(1L, "first-phone", "first-tablet"),
                    target(2L, "second-receiver"),
                    target(3L)));

            assertThat(counterOrZero("partial")).isZero();
            releaseSecondReceiver.countDown();
            awaitDispatchCount("partial", 1.0, 2);
        } finally {
            releaseSecondReceiver.countDown();
        }
    }

    @Test
    void recordsUndeliveredWhenAllDevicesFail() throws Exception {
        given(fcmSender.sendHighPriority("exception", "SOS", "도움이 필요해요", 100L))
                .willThrow(new IllegalStateException("FCM unavailable"));
        given(fcmSender.sendHighPriority("failed", "SOS", "도움이 필요해요", 100L)).willReturn(false);

        service.dispatchSosAsync(List.of(target(1L, "exception", "failed")));

        awaitDispatchCount("undelivered", 1.0, 2);
    }

    @Test
    void distinguishesNoReceiversFromReceiversWithoutDevices() throws Exception {
        service.dispatchSosAsync(List.of());
        service.dispatchSosAsync(List.of(target(1L)));

        awaitDispatchCount("no_receiver", 1.0, 2);
        awaitDispatchCount("undelivered", 1.0, 2);
        verifyNoInteractions(fcmSender);
    }

    @Test
    void doesNotExceedSosPoolConcurrency() throws Exception {
        CountDownLatch poolOccupied = new CountDownLatch(8);
        CountDownLatch releaseDevices = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        given(fcmSender.sendHighPriority(anyString(), anyString(), anyString(), anyLong())).willAnswer(invocation -> {
            maximumActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            poolOccupied.countDown();
            try {
                await(releaseDevices);
                return true;
            } finally {
                active.decrementAndGet();
            }
        });
        String[] tokens = IntStream.range(0, 12).mapToObj(i -> "token-" + i).toArray(String[]::new);

        try {
            service.dispatchSosAsync(List.of(target(1L, tokens)));

            assertThat(poolOccupied.await(2, TimeUnit.SECONDS)).isTrue();
            verify(fcmSender, times(8)).sendHighPriority(anyString(), anyString(), anyString(), anyLong());
            releaseDevices.countDown();
            awaitDispatchCount("delivered", 1.0, 2);
            service.shutdownDispatchExecutors();
            verify(fcmSender, times(12)).sendHighPriority(anyString(), anyString(), anyString(), anyLong());
            assertThat(maximumActive.get()).isEqualTo(8);
        } finally {
            releaseDevices.countDown();
        }
    }

    private NotificationDispatchTarget target(Long receiverId, String... tokens) {
        return new NotificationDispatchTarget(receiverId, "SOS", "도움이 필요해요", 100L, List.of(tokens));
    }

    private void awaitDispatchCount(String result, double expected, long timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline && counterOrZero(result) < expected) {
            Thread.sleep(10);
        }
        assertThat(counterOrZero(result)).isEqualTo(expected);
    }

    private double counterOrZero(String result) {
        var counter = meterRegistry.find("sos_dispatch_total").tag("result", result).counter();
        return counter != null ? counter.count() : 0.0;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Test did not release pending FCM call");
        }
    }
}
