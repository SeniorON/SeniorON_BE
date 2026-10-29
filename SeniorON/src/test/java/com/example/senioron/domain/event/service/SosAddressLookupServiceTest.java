package com.example.senioron.domain.event.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.event.dto.SosAddressLookupRequested;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.event.util.GeocodingClient;
import java.math.BigDecimal;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class SosAddressLookupServiceTest {

    private final GeocodingClient geocodingClient = mock(GeocodingClient.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final com.example.senioron.domain.notification.service.NotificationHomeWebSocketService homeUpdates =
            mock(com.example.senioron.domain.notification.service.NotificationHomeWebSocketService.class);
    private final SosAddressLookupRequested request = new SosAddressLookupRequested(
            1L, new BigDecimal("37.5665"), new BigDecimal("126.9780"));

    @Test
    void fullQueueDoesNotRunLookupOnRequestThreadOrFailCommittedSos() {
        var service = new SosAddressLookupService(geocodingClient, eventRepository, task -> {
            throw new RejectedExecutionException("full");
        }, homeUpdates);

        assertThatCode(() -> service.onAddressLookupRequested(request)).doesNotThrowAnyException();
        verifyNoInteractions(geocodingClient, eventRepository);
    }

    @Test
    void addressUpdateFailureIsContainedInBackgroundTask() {
        given(geocodingClient.reverseGeocode(request.latitude(), request.longitude())).willReturn("서울특별시 중구");
        given(eventRepository.updateAddressByEventId(1L, "서울특별시 중구"))
                .willThrow(new IllegalStateException("DB unavailable"));
        var service = new SosAddressLookupService(geocodingClient, eventRepository, Runnable::run, homeUpdates);

        assertThatCode(() -> service.onAddressLookupRequested(request)).doesNotThrowAnyException();
        verify(eventRepository).updateAddressByEventId(1L, "서울특별시 중구");
        verifyNoInteractions(homeUpdates);
    }

    @Test
    void successfulAddressUpdateNotifiesHomeButDeletedEventDoesNot() {
        given(geocodingClient.reverseGeocode(request.latitude(), request.longitude())).willReturn("서울");
        var service = new SosAddressLookupService(geocodingClient, eventRepository, Runnable::run, homeUpdates);
        service.onAddressLookupRequested(request);
        verifyNoInteractions(homeUpdates);
        given(eventRepository.updateAddressByEventId(1L, "서울")).willReturn(1);
        service.onAddressLookupRequested(request);
        verify(homeUpdates).notifyAddressUpdated(1L);
    }

    @Test
    void missingCoordinatesSkipGeocodingAndStoreUnavailableAddress() {
        var requestWithoutCoordinates = new SosAddressLookupRequested(1L, null, null);
        given(eventRepository.updateAddressByEventId(1L, "위치정보를 확인할 수 없어요")).willReturn(1);
        var service = new SosAddressLookupService(geocodingClient, eventRepository, Runnable::run, homeUpdates);

        service.onAddressLookupRequested(requestWithoutCoordinates);

        verifyNoInteractions(geocodingClient);
        verify(eventRepository).updateAddressByEventId(1L, "위치정보를 확인할 수 없어요");
        verify(homeUpdates).notifyAddressUpdated(1L);
    }
}
