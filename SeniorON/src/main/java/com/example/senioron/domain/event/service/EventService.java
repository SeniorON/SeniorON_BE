package com.example.senioron.domain.event.service;

import com.example.senioron.domain.event.dto.request.InactivityRequest;
import com.example.senioron.domain.event.dto.request.OutingReturnRequest;
import com.example.senioron.domain.event.dto.request.RiskLinkRequest;
import com.example.senioron.domain.event.dto.SosEventCreation;
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
import com.example.senioron.domain.notification.dto.NotificationDispatchResult;
import com.example.senioron.domain.notification.dto.NotificationDispatchTarget;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
@Service
@RequiredArgsConstructor
public class EventService {

    private static final String SOS_EVENT_METRIC = "sos_event_total";
    private static final String TAG_RESULT = "result";

    private final EventRepository eventRepository;
    private final NotificationService notificationService;
    private final GeocodingClient geocodingClient;
    private final SafeBrowsingClient safeBrowsingClient;
    private final ApplicationContext applicationContext;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;

    /**
     * SOS는 긴급 알림, 발송 결과를 응답에 담음.
     * 가족에게 전달되지 못했다면 앱이 직접 연락하도록 안내할 수 있어야 하기 때문.
     * 단, 발송 실패로 HTTP 에러를 반환하지는 않는다. 시니어가 재시도하면
     * SOS 이벤트가 중복 생성되고 자녀에게 중복 푸시가 가기 때문이다.
     */
    public SosEventResponse createSosEvent(User user, SosEventRequest req){
        String address = geocodingClient.reverseGeocode(req.getLatitude(), req.getLongitude());
        EventService self = applicationContext.getBean(EventService.class);

        SosEventCreation creation = self.saveSosEvent(address, user, req);
        countSosEvent("success");

        // 커밋이 끝난 뒤 발송한다. 실패해도 이벤트는 이미 저장되어 있어 인앱 알림으로는 확인할 수 있다.
        NotificationDispatchResult dispatchResult =
                notificationService.dispatchSos(creation.dispatchTargets());

        return SosEventResponse.of(creation.event(), dispatchResult);
    }

    @Transactional
    public SosEventCreation saveSosEvent(String address, User user, SosEventRequest req){
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
        List<NotificationDispatchTarget> dispatchTargets =
                notificationService.prepareSosNotifications(savedEvent);

        return new SosEventCreation(savedEvent, dispatchTargets);
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

    private void countSosEvent(String result) {
        meterRegistry.counter(SOS_EVENT_METRIC, TAG_RESULT, result).increment();
    }
}
