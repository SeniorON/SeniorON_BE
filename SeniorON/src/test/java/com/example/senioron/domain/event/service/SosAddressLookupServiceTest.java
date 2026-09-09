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
    private final SosAddressLookupRequested request = new SosAddressLookupRequested(
            1L, new BigDecimal("37.5665"), new BigDecimal("126.9780"));

    @Test
    void fullQueueDoesNotRunLookupOnRequestThreadOrFailCommittedSos() {
        var service = new SosAddressLookupService(geocodingClient, eventRepository, task -> {
            throw new RejectedExecutionException("full");
        });

        assertThatCode(() -> service.onAddressLookupRequested(request)).doesNotThrowAnyException();
        verifyNoInteractions(geocodingClient, eventRepository);
    }

    @Test
    void addressUpdateFailureIsContainedInBackgroundTask() {
        given(geocodingClient.reverseGeocode(request.latitude(), request.longitude())).willReturn("서울특별시 중구");
        given(eventRepository.updateAddressByEventId(1L, "서울특별시 중구"))
                .willThrow(new IllegalStateException("DB unavailable"));
        var service = new SosAddressLookupService(geocodingClient, eventRepository, Runnable::run);

        assertThatCode(() -> service.onAddressLookupRequested(request)).doesNotThrowAnyException();
        verify(eventRepository).updateAddressByEventId(1L, "서울특별시 중구");
    }
}
