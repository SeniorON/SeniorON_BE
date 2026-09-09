package com.example.senioron.domain.event.service;

import com.example.senioron.domain.event.dto.SosAddressLookupRequested;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.event.util.GeocodingClient;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Slf4j
public class SosAddressLookupService {

    public static final String PENDING_ADDRESS = "주소 확인 중";
    private static final String UNAVAILABLE_ADDRESS = "위치정보를 확인할 수 없어요";

    private final GeocodingClient geocodingClient;
    private final EventRepository eventRepository;
    private final Executor executor;

    public SosAddressLookupService(
            GeocodingClient geocodingClient,
            EventRepository eventRepository,
            @Qualifier("sosAddressExecutor") Executor executor
    ) {
        this.geocodingClient = geocodingClient;
        this.eventRepository = eventRepository;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAddressLookupRequested(SosAddressLookupRequested request) {
        try {
            executor.execute(() -> lookupAndUpdateAddress(request));
        } catch (RejectedExecutionException e) {
            // 큐 포화/종료 시 요청 스레드에서 주소를 조회하면 SOS 발송이 다시 지연된다.
            log.warn("SOS 주소 조회 작업을 등록하지 못했습니다. eventId={}", request.eventId(), e);
        }
    }

    private void lookupAndUpdateAddress(SosAddressLookupRequested request) {
        long startedAt = System.nanoTime();
        String address;
        // 외부 API를 기다리는 동안 DB 트랜잭션/연결을 점유하지 않는다.
        try {
            address = geocodingClient.reverseGeocode(request.latitude(), request.longitude());
            if (address == null || address.isBlank()) {
                address = UNAVAILABLE_ADDRESS;
            }
        } catch (Exception e) {
            log.warn("SOS 주소 조회 실패. eventId={}", request.eventId(), e);
            address = UNAVAILABLE_ADDRESS;
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        log.info("SOS 주소 조회 소요시간={}ms, eventId={}", elapsedMillis, request.eventId());

        try {
            // 이 UPDATE만 별도 트랜잭션으로 실행한다. 삭제된 이벤트는 다시 만들지 않는다.
            eventRepository.updateAddressByEventId(request.eventId(), address);
        } catch (Exception e) {
            log.warn("SOS 주소 저장 실패. eventId={}", request.eventId(), e);
        }
    }
}
