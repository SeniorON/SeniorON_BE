package com.example.senioron.domain.event.service;

import com.example.senioron.domain.event.dto.request.InactivityRequest;
import com.example.senioron.domain.event.dto.request.SosEventRequest;
import com.example.senioron.domain.event.dto.response.InactivityResponse;
import com.example.senioron.domain.event.dto.response.SosEventResponse;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.event.util.GeocodingClient;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.user.entity.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final NotificationService notificationService;
    private final GeocodingClient geocodingClient;
    private final ApplicationContext applicationContext;

    public SosEventResponse createSosEvent(User user, SosEventRequest req){
        String address = geocodingClient.reverseGeocode(req.getLatitude(), req.getLongitude());
        EventService self= applicationContext.getBean(EventService.class);
        return self.saveSosEvent(address, user, req);
    }

    @Transactional
    public SosEventResponse saveSosEvent(String address,User user, SosEventRequest req){
        Event event = Event.builder()
                .user(user)
                .triggeredUser(user)
                .deviceBattery(req.getDeviceBattery())
                .eventType(EventType.SOS)
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .address(address)
                .build();

        Event savedEvent = eventRepository.save(event);
        notificationService.createFormEvent(event);

        return SosEventResponse.of(savedEvent);
    }

    public InactivityResponse createInactivityEvent(User user, InactivityRequest req){
        String address = geocodingClient.reverseGeocode(req.getLatitude(), req.getLongitude());
        EventService self= applicationContext.getBean(EventService.class);
        return self.saveInactivityEvent(address, user, req);
    }

    @Transactional
    public InactivityResponse saveInactivityEvent(String address,User user, InactivityRequest req){
        Event event = Event.builder()
                .user(user)
                .triggeredUser(user)
                .deviceBattery(req.getDeviceBattery())
                .eventType(EventType.INACTIVITY)
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .address(address)
                .lastSeenAt(req.getLastSeenAt())
                .build();

        Event savedEvent = eventRepository.save(event);
        notificationService.createFormEvent(event);

        return InactivityResponse.of(savedEvent);
    }

}
