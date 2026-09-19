package com.example.senioron.domain.event.service;

import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.dto.request.*;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.OutingPhase;
import com.example.senioron.domain.event.entity.RiskCheckResult;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.event.util.*;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.notification.repository.*;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.*;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class EventNotificationPolicyTest {
    private final EventRepository events = mock(EventRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final SeniorRepository seniors = mock(SeniorRepository.class);
    private final FamilyMemberRepository members = mock(FamilyMemberRepository.class);
    private final SafeBrowsingClient safeBrowsing = mock(SafeBrowsingClient.class);
    private final GeocodingClient geocoding = mock(GeocodingClient.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final NotificationService notificationService = new NotificationService(
            notifications, mock(NotificationSettingRepository.class), mock(UserRepository.class),
            seniors, members, mock(DeviceRepository.class), mock(FcmSender.class), metrics,
            mock(com.example.senioron.domain.notification.service.NotificationHomeWebSocketService.class));
    private final EventService service = new EventService(events, notificationService,
            geocoding, safeBrowsing, context, mock(UserRepository.class), seniors, members,
            mock(DeviceRepository.class), metrics);
    private final User parent = User.builder().usersId(10L).role(Role.PARENT).build();

    @BeforeEach
    void setUp() {
        given(context.getBean(EventService.class)).willReturn(service);
        given(events.save(any(Event.class))).willAnswer(call -> {
            Event event = call.getArgument(0);
            ReflectionTestUtils.setField(event, "eventId", 123L);
            return event;
        });
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
        ReflectionTestUtils.invokeMethod(notificationService, "shutdownDispatchExecutors");
        metrics.close();
    }

    @Test
    void unlinkedSosIsSavedAndReturnsNonDispatchReason() {
        var response = service.createSosEvent(parent, new SosEventRequest());
        assertThat(response.getId()).isEqualTo(123L);
        assertThat(response.getSeniorId()).isNull();
        assertThat(response.getNotificationStatus().name()).isEqualTo("NOT_DISPATCHED");
        assertThat(response.getReason()).isEqualTo("SENIOR_NOT_LINKED");
        assertThat(response.getReceiverCount()).isZero();
        ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
        verify(events).save(saved.capture());
        assertThat(saved.getValue().getSenior()).isNull();
        verifyNoInteractions(notifications);
    }

    @Test
    void unlinkedInactivityAndOutingAreSavedWithoutNotifications() {
        var inactivity = service.saveInactivityEvent("주소", parent, new InactivityRequest());
        var outing = service.saveOutingReturnEvent("주소", parent,
                new OutingReturnRequest(OutingPhase.OUTING, BigDecimal.ONE, BigDecimal.ONE, 80));
        assertThat(inactivity.getId()).isEqualTo(123L);
        assertThat(inactivity.getReason()).isEqualTo("SENIOR_NOT_LINKED");
        assertThat(outing.getId()).isEqualTo(123L);
        assertThat(outing.getReason()).isEqualTo("SENIOR_NOT_LINKED");
        assertThat(inactivity.getNotificationStatus().name()).isEqualTo("NOT_DISPATCHED");
        assertThat(outing.getNotificationStatus().name()).isEqualTo("NOT_DISPATCHED");
        verify(events, times(2)).save(any(Event.class));
        verifyNoInteractions(notifications);
    }

    @Test
    void unlinkedDangerousLinkIsSavedWithoutNotifications() {
        RiskLinkRequest request = new RiskLinkRequest();
        ReflectionTestUtils.setField(request, "linkUrl", "https://danger.example");
        given(safeBrowsing.checkUrl(request.getLinkUrl())).willReturn(RiskCheckResult.DANGEROUS);
        var response = service.saveRiskLinkEvent(parent, request);
        assertThat(response.getId()).isEqualTo(123L);
        assertThat(response.getReason()).isEqualTo("SENIOR_NOT_LINKED");
        assertThat(response.getNotificationStatus().name()).isEqualTo("NOT_DISPATCHED");
        verify(events).save(any(Event.class));
        verifyNoInteractions(notifications);
    }

    @Test
    void childCannotCreateAnyEvent() {
        User child = User.builder().usersId(20L).role(Role.CHILD).build();
        assertError(() -> service.createSosEvent(child, new SosEventRequest()), ErrorCode.EVENT_PARENT_ONLY);
        assertError(() -> service.createInactivityEvent(child, new InactivityRequest()), ErrorCode.EVENT_PARENT_ONLY);
        assertError(() -> service.createOutingReturnEvent(child, null), ErrorCode.EVENT_PARENT_ONLY);
        assertError(() -> service.saveRiskLinkEvent(child, new RiskLinkRequest()), ErrorCode.EVENT_PARENT_ONLY);
        verifyNoInteractions(events, geocoding, safeBrowsing, notifications);
    }

    @Test
    void removedParentCannotUseStaleSeniorLink() {
        Family family = Family.builder().familyId(1L).build();
        given(seniors.findByParentUser(parent)).willReturn(Optional.of(
                Senior.builder().seniorId(100L).family(family).parentUser(parent).build()));
        given(members.existsByUserAndFamily(parent, family)).willReturn(false);
        assertError(() -> service.createSosEvent(parent, new SosEventRequest()), ErrorCode.EVENT_SENIOR_ACCESS_DENIED);
        verifyNoInteractions(events, notifications);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
