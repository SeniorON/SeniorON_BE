package com.example.senioron.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;

import com.example.senioron.domain.event.dto.response.EventDetailResponse;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.event.util.GeocodingClient;
import com.example.senioron.domain.event.util.SafeBrowsingClient;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

// 이벤트 상세조회 시 같은 가족이 아니면 EVENT_ACCESS_DENIED로 거부돼야 한다.
class EventServiceDetailAccessTest {

    private static final Long EVENT_ID = 1L;

    private final EventRepository eventRepository = org.mockito.Mockito.mock(EventRepository.class);
    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    private final GeocodingClient geocodingClient = org.mockito.Mockito.mock(GeocodingClient.class);
    private final SafeBrowsingClient safeBrowsingClient = org.mockito.Mockito.mock(SafeBrowsingClient.class);
    private final ApplicationContext applicationContext = org.mockito.Mockito.mock(ApplicationContext.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(
                eventRepository, notificationService, geocodingClient, safeBrowsingClient,
                applicationContext, userRepository, meterRegistry);
    }

    @Test
    void rejectsWithEventAccessDeniedWhenFamiliesDiffer() {
        Family ownerFamily = Family.builder().familyId(1L).build();
        Family otherFamily = Family.builder().familyId(2L).build();

        User eventOwner = User.builder().usersId(1L).role(Role.PARENT).family(ownerFamily).build();
        User outsider = User.builder().usersId(2L).role(Role.CHILD).family(otherFamily).build();

        Event event = Event.builder()
                .eventType(EventType.SOS)
                .triggeredUser(eventOwner)
                .build();

        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(userRepository.findById(outsider.getUsersId())).willReturn(Optional.of(outsider));

        assertThatThrownBy(() -> eventService.getEventDetail(outsider, EVENT_ID))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.EVENT_ACCESS_DENIED);
    }

    @Test
    void allowsWhenSameFamily() {
        Family family = Family.builder().familyId(1L).build();

        User eventOwner = User.builder().usersId(1L).role(Role.PARENT).family(family).build();
        User familyMember = User.builder().usersId(2L).role(Role.CHILD).family(family).build();

        Event event = Event.builder()
                .eventType(EventType.SOS)
                .triggeredUser(eventOwner)
                .build();

        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(userRepository.findById(familyMember.getUsersId())).willReturn(Optional.of(familyMember));

        EventDetailResponse response = eventService.getEventDetail(familyMember, EVENT_ID);

        assertThat(response).isNotNull();
    }
}
