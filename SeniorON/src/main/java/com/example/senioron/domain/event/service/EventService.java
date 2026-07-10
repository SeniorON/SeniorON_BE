package com.example.senioron.domain.event.service;

import com.example.senioron.domain.event.dto.request.SosEventRequest;
import com.example.senioron.domain.event.dto.response.SosEventResponse;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.user.entity.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final NotificationService notificationService;

    @Transactional
    public SosEventResponse createSosEvent(User user, SosEventRequest req){
        Event event = Event.builder()
                .user(user)
                .triggeredUser(user)
                .deviceBattery(req.getDeviceBattery())
                .eventType(EventType.SOS)
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .build();

        Event savedEvent = eventRepository.save(event);
        notificationService.createFormEvent(event);
        return SosEventResponse.of(savedEvent);
    }
}
