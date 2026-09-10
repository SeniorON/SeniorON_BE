package com.example.senioron.domain.event.service;

import com.example.senioron.domain.event.dto.request.InactivityRequest;
import com.example.senioron.domain.event.dto.request.OutingReturnRequest;
import com.example.senioron.domain.event.dto.request.RiskLinkRequest;
import com.example.senioron.domain.event.dto.SosEventCreation;
import com.example.senioron.domain.event.dto.SosAddressLookupRequested;
import com.example.senioron.domain.event.dto.request.SosEventRequest;
import com.example.senioron.domain.device.repository.DeviceRepository;
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
    private final DeviceRepository deviceRepository;
    private final MeterRegistry meterRegistry;

    /**
     * SOS 이벤트와 인앱 알림을 먼저 커밋하고, FCM은 전용 풀에서 비동기로 발송한다.
     * 푸시 실패는 API 실패로 바꾸지 않고 로그와 메트릭으로 관찰한다.
     */
    public SosEventResponse createSosEvent(User user, SosEventRequest req){
        EventService self = applicationContext.getBean(EventService.class);

        SosEventCreation creation = self.saveSosEvent(user, req);
        countSosEvent("success");

        // 커밋이 끝난 뒤 전용 풀에 발송을 맡기고 결과를 기다리지 않는다.
        notificationService.dispatchSosAsync(creation.dispatchTargets());

        return SosEventResponse.of(creation.event(), creation.dispatchTargets().size());
    }

    @Transactional
    public SosEventCreation saveSosEvent(User user, SosEventRequest req){
        Event event = Event.builder()
                .user(user)
                .triggeredUser(user)
                .deviceBattery(req.getDeviceBattery())
                .eventType(EventType.SOS)
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .address(SosAddressLookupService.PENDING_ADDRESS)
                .build();

        Event savedEvent = eventRepository.save(event);
        List<NotificationDispatchTarget> dispatchTargets =
                notificationService.prepareSosNotifications(savedEvent);

        // 롤백 시 조회하지 않으며, 커밋 후 주소 조회 결과를 기다리지 않고 발송한다.
        applicationContext.publishEvent(new SosAddressLookupRequested(
                savedEvent.getEventId(), savedEvent.getLatitude(), savedEvent.getLongitude()));

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

    /**
     * 세이프 브라우징 검사가 실패(UNAVAILABLE)해도 이벤트는 항상 저장한다.
     * SOS와 동일한 원칙: 외부 API 장애로 알람 기록 자체가 유실되면 안 되기 때문이다.
     * 이 경우 isDangerous는 null("모름")로 남고, 위험이 확인된 경우에만 알림을 보낸다.
     */
    @Transactional
    public RiskLinkResponse saveRiskLinkEvent(User user, RiskLinkRequest req) {

        RiskCheckResult result = safeBrowsingClient.checkUrl(req.getLinkUrl());
        Boolean isDangerous = switch (result) {
            case DANGEROUS -> true;
            case SAFE -> false;
            case UNAVAILABLE -> null;
        };

        Event event = Event.builder()
                .user(user)
                .triggeredUser(user)
                .eventType(EventType.RISK_LINK)
                .linkUrl(req.getLinkUrl())
                .isDangerous(isDangerous)
                .deviceBattery(req.getDeviceBattery())
                .build();

        Event savedEvent = eventRepository.save(event);
        if (Boolean.TRUE.equals(isDangerous)) {
            notificationService.createFormEvent(savedEvent);
        }

        return RiskLinkResponse.of(savedEvent);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(User user, Long eventId){
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        validateAccess(user, event);
        Integer currentBatteryLevel = deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(event.getTriggeredUser())
                .map(device -> device.getBatteryLevel())
                .orElse(null);
        return EventDetailResponse.of(event, currentBatteryLevel);
    }

    private void validateAccess(User principal, Event event) {
        User currentUser = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        User eventOwner = event.getTriggeredUser();

        if (eventOwner.getFamily() == null || currentUser.getFamily() == null
                || !Objects.equals(currentUser.getFamily().getFamilyId(), eventOwner.getFamily().getFamilyId())) {
            throw new BusinessException(ErrorCode.EVENT_ACCESS_DENIED);
        }
    }

    private void countSosEvent(String result) {
        meterRegistry.counter(SOS_EVENT_METRIC, TAG_RESULT, result).increment();
    }
}
