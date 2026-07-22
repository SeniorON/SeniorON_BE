package com.example.senioron.domain.event.service;

import com.example.senioron.domain.event.dto.request.InactivityRequest;
import com.example.senioron.domain.event.dto.request.OutingReturnRequest;
import com.example.senioron.domain.event.dto.request.RiskLinkRequest;
import com.example.senioron.domain.event.dto.request.SosEventRequest;
import com.example.senioron.domain.event.dto.response.EventDetailResponse;
import com.example.senioron.domain.event.dto.response.InactivityResponse;
import com.example.senioron.domain.event.dto.response.OutingReturnResponse;
import com.example.senioron.domain.event.dto.response.RiskLinkResponse;
import com.example.senioron.domain.event.dto.response.SosEventResponse;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.entity.RiskCheckResult;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.event.util.GeocodingClient;
import com.example.senioron.domain.event.util.SafeBrowsingClient;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final NotificationService notificationService;
    private final GeocodingClient geocodingClient;
    private final SafeBrowsingClient safeBrowsingClient;
    private final ApplicationContext applicationContext;
    private final UserRepository userRepository;

    public SosEventResponse createSosEvent(User user, SosEventRequest req){
        String address = geocodingClient.reverseGeocode(req.getLatitude(), req.getLongitude());
        EventService self = applicationContext.getBean(EventService.class);
        return self.saveSosEvent(address, user, req);
    }

    @Transactional
    public SosEventResponse saveSosEvent(String address, User user, SosEventRequest req){
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
        notificationService.createFormEvent(savedEvent);

        return SosEventResponse.of(savedEvent);
    }

    public InactivityResponse createInactivityEvent(User user, InactivityRequest req){
        String address = geocodingClient.reverseGeocode(req.getLatitude(), req.getLongitude());
        EventService self = applicationContext.getBean(EventService.class);
        return self.saveInactivityEvent(address, user, req);
    }

    @Transactional
    public InactivityResponse saveInactivityEvent(String address, User user, InactivityRequest req){
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
        notificationService.createFormEvent(savedEvent);

        return InactivityResponse.of(savedEvent);
    }

    public OutingReturnResponse createOutingReturnEvent(User user, OutingReturnRequest req) {
        String address = geocodingClient.reverseGeocode(req.getLatitude(), req.getLongitude());
        EventService self = applicationContext.getBean(EventService.class);
        return self.saveOutingReturnEvent(address, user, req);
    }

    @Transactional
    public OutingReturnResponse saveOutingReturnEvent(String address, User user, OutingReturnRequest req) {
        Event event = Event.builder()
                .user(user)
                .triggeredUser(user)
                .eventType(EventType.OUTING_RETURN)
                .phase(req.getPhase())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .deviceBattery(req.getDeviceBattery())
                .address(address)
                .build();

        Event savedEvent = eventRepository.save(event);
        notificationService.createFormEvent(savedEvent);

        return OutingReturnResponse.of(savedEvent);
    }

    @Transactional
    public RiskLinkResponse saveRiskLinkEvent(User user, RiskLinkRequest req) {

        RiskCheckResult result = safeBrowsingClient.checkUrl(req.getLinkUrl());

        if (result == RiskCheckResult.UNAVAILABLE) {
            throw new BusinessException(ErrorCode.RISK_LINK_CHECK_UNAVAILABLE);
        }

        boolean isDangerous = result == RiskCheckResult.DANGEROUS;

        Event event = Event.builder()
                .user(user)
                .triggeredUser(user)
                .eventType(EventType.RISK_LINK)
                .linkUrl(req.getLinkUrl())
                .isDangerous(isDangerous)
                .deviceBattery(req.getDeviceBattery())
                .build();

        Event savedEvent = eventRepository.save(event);
        if (isDangerous) {
            notificationService.createFormEvent(savedEvent);
        }

        return RiskLinkResponse.of(savedEvent);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(User user, Long eventId){
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        validateAccess(user, event);
        return EventDetailResponse.of(event);
    }

    private void validateAccess(User principal, Event event) {
        User currentUser = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        User eventOwner = event.getTriggeredUser();

        if (eventOwner.getFamily() == null || currentUser.getFamily() == null
                || !Objects.equals(currentUser.getFamily().getFamilyId(), eventOwner.getFamily().getFamilyId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}