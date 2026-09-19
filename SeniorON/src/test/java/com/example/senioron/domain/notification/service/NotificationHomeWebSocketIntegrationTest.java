package com.example.senioron.domain.notification.service;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.jwt.JwtUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "cloud.aws.region=ap-northeast-2", "cloud.aws.s3.bucket=test-bucket",
        "spring.datasource.url=jdbc:h2:mem:notification-websocket;MODE=MariaDB;DB_CLOSE_DELAY=-1"
})
@Timeout(25)
class NotificationHomeWebSocketIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private JwtUtil jwt;
    @Autowired private SimpUserRegistry registry;
    @Autowired private TransactionTemplate tx;
    @Autowired private UserRepository users;
    @Autowired private FamilyRepository families;
    @Autowired private FamilyMemberRepository members;
    @Autowired private SeniorRepository seniors;
    @Autowired private EventRepository events;
    @Autowired private NotificationRepository notifications;
    @Autowired private NotificationService service;
    @Autowired private NotificationHomeWebSocketService homeUpdates;
    private final List<Client> clients = new ArrayList<>();
    private User child;
    private User otherChild;
    private User outsider;
    private Senior senior;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> {
            child = user(Role.CHILD);
            otherChild = user(Role.CHILD);
            outsider = user(Role.CHILD);
            senior = senior(child);
            members.save(FamilyMember.builder().user(otherChild).family(senior.getFamily())
                    .managerType(ManagerType.NONE).build());
        });
    }

    @AfterEach
    void closeConnections() {
        clients.forEach(client -> client.socket.abort());
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    void committedNotificationReachesEachReceiverWithoutDeviceToken(EventType type) throws Exception {
        Client first = connect(child);
        Client second = connect(otherChild);
        Client unrelated = connect(outsider);
        Long eventId = tx.execute(status -> {
            Long id = createEvent(senior, type);
            assertThat(first.frames).isEmpty();
            assertThat(second.frames).isEmpty();
            return id;
        });
        String firstMessage = first.next();
        String secondMessage = second.next();
        assertThat(firstMessage).startsWith("MESSAGE").contains("NOTIFICATION_HOME_UPDATED", "CREATED",
                "\"seniorId\":" + senior.getSeniorId(), "\"eventId\":" + eventId, "\"notificationId\":");
        assertThat(secondMessage).startsWith("MESSAGE").contains("\"eventId\":" + eventId);
        assertThat(secondMessage).isNotEqualTo(firstMessage);
        assertThat(unrelated.frames.poll(250, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void rollbackDoesNotSendAndDoesNotPersistNotification() throws Exception {
        Client client = connect(child);
        long count = notifications.count();
        tx.executeWithoutResult(status -> {
            createEvent(senior, EventType.SOS);
            status.setRollbackOnly();
        });
        assertThat(client.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(notifications.count()).isEqualTo(count);
    }

    @Test
    void sameUserGetsDistinctSeniorIdsAcrossFamilies() throws Exception {
        Senior another = tx.execute(status -> senior(child));
        Client client = connect(child);
        tx.executeWithoutResult(status -> createEvent(senior, EventType.SOS));
        assertThat(client.next()).contains("\"seniorId\":" + senior.getSeniorId());
        tx.executeWithoutResult(status -> createEvent(another, EventType.SOS));
        assertThat(client.next()).contains("\"seniorId\":" + another.getSeniorId());
    }

    @Test
    void addressUpdateNotifiesOnlyExistingRecipientsStillInFamily() throws Exception {
        Client client = connect(child);
        Client removed = connect(otherChild);
        Long eventId = tx.execute(status -> createEvent(senior, EventType.SOS));
        client.next();
        removed.next();
        tx.executeWithoutResult(status -> members.deleteByUserAndFamily(otherChild, senior.getFamily()));
        // 신규 가족 구성원에게 과거에 받지 않은 알림을 보내지 않는다.
        tx.executeWithoutResult(status -> members.save(FamilyMember.builder().user(outsider)
                .family(senior.getFamily()).managerType(ManagerType.SUB).build()));
        Client newMember = connect(outsider);
        events.updateAddressByEventId(eventId, "서울");
        homeUpdates.notifyAddressUpdated(eventId);
        assertThat(client.next()).contains("ADDRESS_UPDATED", "\"eventId\":" + eventId);
        assertThat(events.findById(eventId).orElseThrow().getAddress()).isEqualTo("서울");
        assertThat(removed.frames.poll(250, TimeUnit.MILLISECONDS)).isNull();
        assertThat(newMember.frames.poll(250, TimeUnit.MILLISECONDS)).isNull();
    }

    private User user(Role role) {
        return users.save(User.builder().loginId("ws-" + java.util.UUID.randomUUID())
                .name("테스트").role(role).build());
    }

    private Senior senior(User manager) {
        Family family = families.save(Family.builder().seniorCode(java.util.UUID.randomUUID().toString()).build());
        User parent = user(Role.PARENT);
        members.save(FamilyMember.builder().user(manager).family(family).managerType(ManagerType.PRIMARY).build());
        return seniors.save(Senior.builder().name("시니어").birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678").family(family).parentUser(parent).registeredBy(manager).build());
    }

    private Long createEvent(Senior senior, EventType type) {
        Event event = events.save(Event.builder().user(senior.getParentUser()).triggeredUser(senior.getParentUser())
                .senior(senior).eventType(type).address("주소 확인 중").build());
        var result = type == EventType.SOS ? service.prepareSosNotificationResult(event) : service.createFormEvent(event);
        assertThat(result.reason()).isEqualTo("NO_DEVICE_TOKEN");
        return event.getEventId();
    }

    private Client connect(User user) throws Exception {
        Client client = new Client();
        clients.add(client);
        client.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), client).get(5, TimeUnit.SECONDS);
        client.socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer "
                + jwt.createAccessToken(user) + "\n\n\0", true).join();
        assertThat(client.next()).startsWith("CONNECTED");
        client.socket.sendText("SUBSCRIBE\nid:home\ndestination:/user/queue/notification-home\n\n\0", true).join();
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            var connected = registry.getUser(user.getUsersId().toString());
            return connected != null && connected.getSessions().stream()
                    .anyMatch(session -> !session.getSubscriptions().isEmpty());
        });
        return client;
    }

    private static class Client implements WebSocket.Listener {
        private WebSocket socket;
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            buffer.append(data);
            int end;
            while ((end = buffer.indexOf("\0")) >= 0) {
                frames.add(buffer.substring(0, end).stripLeading());
                buffer.delete(0, end + 1);
            }
            socket.request(1);
            return null;
        }

        private String next() throws InterruptedException {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertThat(frame).as("STOMP frame").isNotNull();
            return frame;
        }
    }
}
