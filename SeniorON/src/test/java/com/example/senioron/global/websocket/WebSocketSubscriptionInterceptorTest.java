package com.example.senioron.global.websocket;

import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.jwt.JwtUtil;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketSubscriptionInterceptorTest {
    private final UserRepository users = mock(UserRepository.class);
    private final JwtUtil jwt = new JwtUtil("websocket-test-secret-key-at-least-32-characters", 60000, 120000);
    private final WebSocketSubscriptionInterceptor interceptor = new WebSocketSubscriptionInterceptor(jwt, users);
    private final User child = User.builder().usersId(10L).role(Role.CHILD).build();

    @Test
    void accessTokenUsesStableUserIdAndAllowsOnlyOwnNotificationChannel() {
        when(users.findById(10L)).thenReturn(Optional.of(child));
        var connect = frame(StompCommand.CONNECT, null);
        connect.setNativeHeader("Authorization", "Bearer " + jwt.createAccessToken(child));
        send(connect);
        assertThat(connect.getUser().getName()).isEqualTo("10");
        var subscribe = frame(StompCommand.SUBSCRIBE, "/user/queue/notification-home");
        subscribe.setUser(connect.getUser());
        assertThatCode(() -> send(subscribe)).doesNotThrowAnyException();
        for (String path : new String[]{"/queue/notification-home", "/queue/notification-home-user-other",
                "/user/20/queue/notification-home", "/topic/**", "/topic/senior/20/home"}) {
            var forbidden = frame(StompCommand.SUBSCRIBE, path);
            forbidden.setUser(connect.getUser());
            assertThatThrownBy(() -> send(forbidden)).isInstanceOf(AccessDeniedException.class);
        }
        var publish = frame(StompCommand.SEND, "/user/queue/notification-home");
        publish.setUser(connect.getUser());
        assertThatThrownBy(() -> send(publish)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingInvalidExpiredAndRefreshTokensAreRejected() {
        JwtUtil expired = new JwtUtil("websocket-test-secret-key-at-least-32-characters", -10000, 120000);
        for (String token : new String[]{"Bearer invalid", "Bearer " + jwt.createRefreshToken(child),
                "Bearer " + expired.createAccessToken(child), "Basic invalid"}) {
            var connect = frame(StompCommand.CONNECT, null);
            connect.setNativeHeader("Authorization", token);
            assertThatThrownBy(() -> send(connect)).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> send(frame(StompCommand.CONNECT, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> send(frame(StompCommand.SUBSCRIBE, "/user/queue/notification-home")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void withdrawnAccountCannotConnect() {
        when(users.findById(10L)).thenReturn(Optional.of(User.builder().usersId(10L)
                .role(Role.CHILD).status(UserStatus.WITHDRAWN).build()));
        var connect = frame(StompCommand.CONNECT, null);
        connect.setNativeHeader("Authorization", "Bearer " + jwt.createAccessToken(child));
        assertThatThrownBy(() -> send(connect)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void existingHttpAuthenticatedParentHomeStillWorksButNotificationChannelIsDenied() {
        User parent = User.builder().usersId(20L).role(Role.PARENT).build();
        when(users.findById(20L)).thenReturn(Optional.of(parent));
        var connect = frame(StompCommand.CONNECT, null);
        connect.setUser(new UsernamePasswordAuthenticationToken(parent, null, java.util.List.of()));
        send(connect);
        var home = frame(StompCommand.SUBSCRIBE, "/topic/senior/20/home");
        home.setUser(connect.getUser());
        assertThatCode(() -> send(home)).doesNotThrowAnyException();
        var notifications = frame(StompCommand.SUBSCRIBE, "/user/queue/notification-home");
        notifications.setUser(connect.getUser());
        assertThatThrownBy(() -> send(notifications)).isInstanceOf(AccessDeniedException.class);
    }

    private StompHeaderAccessor frame(StompCommand command, String destination) {
        var frame = StompHeaderAccessor.create(command);
        if (destination != null) frame.setDestination(destination);
        frame.setLeaveMutable(true);
        return frame;
    }

    private void send(StompHeaderAccessor frame) {
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], frame.getMessageHeaders()), null);
    }
}
