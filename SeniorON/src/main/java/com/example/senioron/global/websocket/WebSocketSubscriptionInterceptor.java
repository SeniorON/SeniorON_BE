package com.example.senioron.global.websocket;

import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.domain.notification.service.NotificationHomeWebSocketService;
import com.example.senioron.global.jwt.JwtUtil;
import io.jsonwebtoken.JwtException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern SENIOR_HOME_TOPIC_PATTERN =
            Pattern.compile("^/topic/senior/(\\d+)/home$");
    private final JwtUtil jwtUtil;
    private final UserRepository users;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            User user = authenticate(accessor);
            // 기본 User.toString()이 아닌 안정적인 usersId로 사용자별 세션을 라우팅한다.
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()) {
                @Override
                public String getName() {
                    return user.getUsersId().toString();
                }
            });
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            User user = authenticatedUser(accessor);
            String destination = accessor.getDestination();
            if (("/user" + NotificationHomeWebSocketService.DESTINATION).equals(destination)) {
                if (user.getRole() != Role.CHILD) throw denied();
                return message;
            }
            var matcher = SENIOR_HOME_TOPIC_PATTERN.matcher(destination == null ? "" : destination);
            // 기존 부모 홈 채널은 usersId 계약을 유지한다.
            if (!matcher.matches() || !user.getUsersId().toString().equals(matcher.group(1))) {
                // /queue 직접 접근, 다른 사용자 /user/{id}, 와일드카드 구독도 차단한다.
                throw denied();
            }
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            // 현재 채널은 서버→클라이언트 갱신 전용이다.
            throw denied();
        }
        return message;
    }

    private User authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        Long userId;
        if (authorization != null) {
            if (!authorization.startsWith("Bearer ")) throw denied();
            try {
                var claims = jwtUtil.parseClaims(authorization.substring(7));
                if (!jwtUtil.isAccessToken(claims)) throw denied();
                userId = jwtUtil.getUsersId(claims);
            } catch (JwtException | IllegalArgumentException exception) {
                throw denied();
            }
        } else {
            // HTTP handshake에 JWT를 싣는 기존 네이티브 앱도 지원한다.
            userId = authenticatedUser(accessor).getUsersId();
        }
        return users.findById(userId).filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(WebSocketSubscriptionInterceptor::denied);
    }

    private static User authenticatedUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        throw denied();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("WebSocket 인증 또는 채널 접근 권한이 없습니다.");
    }
}
