package com.example.senioron.domain.notification.service;

import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.notification.dto.NotificationDispatchResult;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void returnsOnFirstSuccessWhileEarlierSlowDeviceContinuesSending() throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        CountDownLatch slowFinished = new CountDownLatch(1);
        given(fcmSender.sendHighPriority("slow", "SOS", "도움이 필요해요", 100L)).willAnswer(invocation -> {
            slowStarted.countDown();
            await(releaseSlow);
            slowFinished.countDown();
            return true;
        });
        given(fcmSender.sendHighPriority("fast", "SOS", "도움이 필요해요", 100L)).willAnswer(invocation -> {
            await(slowStarted);
            return true;
        });

        try {
            var response = requestExecutor.submit(() -> service.dispatchSos(List.of(target(1L, "slow", "fast"))));

            assertThat(response.get(2, TimeUnit.SECONDS)).isEqualTo(new NotificationDispatchResult(1, 1));
            assertThat(slowFinished.getCount()).isEqualTo(1);
            assertThat(dispatchCount("delivered")).isEqualTo(1);

            releaseSlow.countDown();
            assertThat(slowFinished.await(2, TimeUnit.SECONDS)).isTrue();
            service.shutdownDispatchExecutors();
            // 같은 수신자의 늦은 성공을 별도 성공으로 중복 집계하지 않는다.
            assertThat(dispatchCount("delivered")).isEqualTo(1);
            verify(fcmSender).sendHighPriority("slow", "SOS", "도움이 필요해요", 100L);
            verify(fcmSender).sendHighPriority("fast", "SOS", "도움이 필요해요", 100L);
        } finally {
            releaseSlow.countDown();
        }
    }

    @Test
    void earlyFailureDoesNotHideAnotherDevicesLaterSuccess() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch releaseSuccess = new CountDownLatch(1);
        given(fcmSender.sendHighPriority("failed", "SOS", "도움이 필요해요", 100L)).willAnswer(invocation -> {
            failed.countDown();
            return false;
        });
        given(fcmSender.sendHighPriority("pending", "SOS", "도움이 필요해요", 100L)).willAnswer(invocation -> {
            await(releaseSuccess);
            return true;
        });

        try {
            var response = requestExecutor.submit(() -> service.dispatchSos(List.of(target(1L, "failed", "pending"))));

            assertThat(failed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> response.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseSuccess.countDown();
            assertThat(response.get(2, TimeUnit.SECONDS)).isEqualTo(new NotificationDispatchResult(1, 1));
        } finally {
            releaseSuccess.countDown();
        }
    }

    @Test
    void waitsForOtherReceiversAndCountsPeopleRatherThanDevices() throws Exception {
        CountDownLatch firstReceiverSent = new CountDownLatch(2);
        CountDownLatch releaseSecondReceiver = new CountDownLatch(1);
        given(fcmSender.sendHighPriority(anyString(), anyString(), anyString(), anyLong())).willAnswer(invocation -> {
            if ("second-receiver".equals(invocation.getArgument(0))) {
                await(releaseSecondReceiver);
            } else {
                firstReceiverSent.countDown();
            }
            return true;
        });

        try {
            var response = requestExecutor.submit(() -> service.dispatchSos(List.of(
                    target(1L, "first-phone", "first-tablet"), target(2L, "second-receiver"), target(3L))));

            assertThat(firstReceiverSent.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> response.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseSecondReceiver.countDown();

            assertThat(response.get(2, TimeUnit.SECONDS)).isEqualTo(new NotificationDispatchResult(3, 2));
            assertThat(dispatchCount("partial")).isEqualTo(1);
        } finally {
            releaseSecondReceiver.countDown();
        }
    }

    @Test
    void allFailedDevicesIncludingExceptionsProduceUndeliveredResult() {
        given(fcmSender.sendHighPriority("exception", "SOS", "도움이 필요해요", 100L))
                .willThrow(new IllegalStateException("FCM unavailable"));
        given(fcmSender.sendHighPriority("failed", "SOS", "도움이 필요해요", 100L)).willReturn(false);

        assertThat(service.dispatchSos(List.of(target(1L, "exception", "failed"))))
                .isEqualTo(new NotificationDispatchResult(1, 0));
        assertThat(dispatchCount("undelivered")).isEqualTo(1);
    }

    @Test
    void distinguishesNoReceiversFromReceiversWithoutDevices() {
        assertThat(service.dispatchSos(List.of())).isEqualTo(new NotificationDispatchResult(0, 0));
        assertThat(service.dispatchSos(List.of(target(1L)))).isEqualTo(new NotificationDispatchResult(1, 0));
        assertThat(dispatchCount("no_receiver")).isEqualTo(1);
        assertThat(dispatchCount("undelivered")).isEqualTo(1);
        verifyNoInteractions(fcmSender);
    }

    @Test
    void sendsQueuedDevicesAfterEarlyResponseWithoutExceedingPoolConcurrency() throws Exception {
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
            var response = requestExecutor.submit(() -> service.dispatchSos(List.of(target(1L, tokens))));

            assertThat(poolOccupied.await(2, TimeUnit.SECONDS)).isTrue();
            verify(fcmSender, times(8)).sendHighPriority(anyString(), anyString(), anyString(), anyLong());
            releaseDevices.countDown();
            assertThat(response.get(2, TimeUnit.SECONDS)).isEqualTo(new NotificationDispatchResult(1, 1));

            service.shutdownDispatchExecutors();
            verify(fcmSender, times(12)).sendHighPriority(anyString(), anyString(), anyString(), anyLong());
            assertThat(maximumActive.get()).isEqualTo(8);
        } finally {
            releaseDevices.countDown();
        }
    }

    @Test
    void retainsResponseTimeoutWhenNoDeviceHasCompleted() throws Exception {
        CountDownLatch releaseDevice = new CountDownLatch(1);
        given(fcmSender.sendHighPriority("pending", "SOS", "도움이 필요해요", 100L)).willAnswer(invocation -> {
            await(releaseDevice);
            return true;
        });

        try {
            var response = requestExecutor.submit(() -> service.dispatchSos(List.of(target(1L, "pending"))));

            assertThat(response.get(7, TimeUnit.SECONDS)).isEqualTo(new NotificationDispatchResult(1, 0));
            releaseDevice.countDown();
            service.shutdownDispatchExecutors();
            assertThat(dispatchCount("undelivered")).isEqualTo(1);
            assertThat(meterRegistry.find("sos_dispatch_total").tag("result", "delivered").counter()).isNull();
        } finally {
            releaseDevice.countDown();
        }
    }

    private NotificationDispatchTarget target(Long receiverId, String... tokens) {
        return new NotificationDispatchTarget(receiverId, "SOS", "도움이 필요해요", 100L, List.of(tokens));
    }

    private double dispatchCount(String result) {
        return meterRegistry.get("sos_dispatch_total").tag("result", result).counter().count();
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Test did not release pending FCM call");
        }
    }
}
