package com.example.senioron.global.websocket;

import com.example.senioron.domain.user.entity.User;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern SENIOR_HOME_TOPIC_PATTERN =
            Pattern.compile("^/topic/senior/(\\d+)/home$");

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(message);

        // 구독 요청일 때만 검사
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {

            String destination = accessor.getDestination();

            if (destination == null
                    || !destination.startsWith("/topic/senior/")) {
                return message;
            }

            Matcher matcher =
                    SENIOR_HOME_TOPIC_PATTERN.matcher(destination);

            // 잘못된 주소
            if (!matcher.matches()) {
                throw new AccessDeniedException(
                        "허용되지 않은 WebSocket 구독 경로입니다."
                );
            }

            // 현재 WebSocket에 로그인된 사용자
            if (!(accessor.getUser() instanceof Authentication authentication)) {
                throw new AccessDeniedException(
                        "WebSocket 인증이 필요합니다."
                );
            }

            if (!(authentication.getPrincipal() instanceof User user)) {
                throw new AccessDeniedException(
                        "사용자 정보를 확인할 수 없습니다."
                );
            }

            Long requestedSeniorUserId =
                    Long.valueOf(matcher.group(1));

            // 로그인한 사용자 ID와 구독하려는 시니어 ID 비교
            if (!user.getUsersId().equals(requestedSeniorUserId)) {
                throw new AccessDeniedException(
                        "다른 사용자의 홈 채널을 구독할 수 없습니다."
                );
            }
        }

        return message;
    }
}