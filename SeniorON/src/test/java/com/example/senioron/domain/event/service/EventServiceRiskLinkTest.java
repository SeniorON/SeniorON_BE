package com.example.senioron.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.event.dto.request.RiskLinkRequest;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.dto.response.RiskLinkResponse;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.entity.RiskCheckResult;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.event.util.GeocodingClient;
import com.example.senioron.domain.event.util.SafeBrowsingClient;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 세이프 브라우징 검사 결과(DANGEROUS/SAFE/UNAVAILABLE)에 따라 이벤트가 항상 저장되고,
 * 알림은 위험이 확인된 경우에만 발송되는지 검증한다.
 * UNAVAILABLE(API 장애 등)이어도 예외를 던지지 않고 isDangerous=null로 저장해야 한다 —
 * SOS와 동일하게 다운스트림 실패로 알람 기록 자체가 유실되면 안 되기 때문이다.
 */
class EventServiceRiskLinkTest {

    private final EventRepository eventRepository = org.mockito.Mockito.mock(EventRepository.class);
    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    private final GeocodingClient geocodingClient = org.mockito.Mockito.mock(GeocodingClient.class);
    private final SafeBrowsingClient safeBrowsingClient = org.mockito.Mockito.mock(SafeBrowsingClient.class);
    private final ApplicationContext applicationContext = org.mockito.Mockito.mock(ApplicationContext.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final DeviceRepository deviceRepository = org.mockito.Mockito.mock(DeviceRepository.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private EventService eventService;
    private User senior;

    @BeforeEach
    void setUp() {
        eventService = new EventService(
                eventRepository, notificationService, geocodingClient, safeBrowsingClient,
                applicationContext, userRepository, deviceRepository, meterRegistry);

        senior = User.builder().usersId(1L).name("시니어").role(Role.PARENT).build();

        given(eventRepository.save(any(Event.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void dangerousLink_savesEventAndNotifiesFamily() {
        given(safeBrowsingClient.checkUrl("https://danger.example")).willReturn(RiskCheckResult.DANGEROUS);

        RiskLinkResponse response = eventService.saveRiskLinkEvent(senior, riskLinkRequest("https://danger.example"));

        assertThat(response.getRiskLevel()).isEqualTo("높음");
        verify(eventRepository).save(argThatEventWith(EventType.RISK_LINK, Boolean.TRUE));
        verify(notificationService).createFormEvent(any(Event.class));
    }

    @Test
    void safeLink_savesEventAndDoesNotNotify() {
        given(safeBrowsingClient.checkUrl("https://safe.example")).willReturn(RiskCheckResult.SAFE);

        RiskLinkResponse response = eventService.saveRiskLinkEvent(senior, riskLinkRequest("https://safe.example"));

        assertThat(response.getRiskLevel()).isEqualTo("낮음");
        verify(eventRepository).save(argThatEventWith(EventType.RISK_LINK, Boolean.FALSE));
        verify(notificationService, never()).createFormEvent(any(Event.class));
    }

    @Test
    void checkUnavailable_stillSavesEventWithUnknownRiskAndDoesNotNotify() {
        given(safeBrowsingClient.checkUrl("https://unknown.example")).willReturn(RiskCheckResult.UNAVAILABLE);

        RiskLinkResponse response = eventService.saveRiskLinkEvent(senior, riskLinkRequest("https://unknown.example"));

        assertThat(response.getId()).isNull(); // 저장 자체는 됐지만 mock save는 eventId를 채우지 않음
        assertThat(response.getRiskLevel()).isEqualTo("확인불가");
        verify(eventRepository).save(argThatEventWith(EventType.RISK_LINK, null));
        verify(notificationService, never()).createFormEvent(any(Event.class));
    }

    private Event argThatEventWith(EventType expectedType, Boolean expectedIsDangerous) {
        return org.mockito.ArgumentMatchers.argThat(event ->
                event != null
                        && event.getEventType() == expectedType
                        && java.util.Objects.equals(event.getIsDangerous(), expectedIsDangerous));
    }

    private RiskLinkRequest riskLinkRequest(String linkUrl) {
        RiskLinkRequest req = new RiskLinkRequest();
        ReflectionTestUtils.setField(req, "linkUrl", linkUrl);
        ReflectionTestUtils.setField(req, "deviceBattery", 80);
        return req;
    }
}
