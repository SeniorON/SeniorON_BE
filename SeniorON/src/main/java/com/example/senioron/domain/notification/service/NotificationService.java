package com.example.senioron.domain.notification.service;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.entity.OutingPhase;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.notification.dto.response.NotificationHomeResponse;
import com.example.senioron.domain.notification.dto.response.NotificationSettingResponse;
import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationSetting;
import com.example.senioron.domain.notification.entity.NotificationSettingType;
import com.example.senioron.domain.notification.entity.NotificationType;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import com.example.senioron.domain.notification.repository.NotificationSettingRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;
    private final FcmSender fcmSender;

    @Transactional
    public void createFormEvent(Event event) {
        User sender = event.getTriggeredUser();
        if(sender.getFamily() == null) return;

        List<User> receivers = userRepository.findByFamilyAndUsersIdNotAndRole(sender.getFamily(), sender.getUsersId(), Role.CHILD);
        if (receivers.isEmpty()) return;

        NotificationType type = resolveType(event.getEventType());
        String title = resolveTitle(event.getEventType());
        String body = resolveBody(event.getEventType(), event.getPhase());
        List<Notification> notifications = new ArrayList<>();
        for (User receiver : receivers) {
            if (!isEnabled(receiver, type)) {
                continue;
            }
            Notification notification = Notification.builder()
                    .event(event)
                    .sendUser(sender)
                    .receiverUser(receiver)
                    .notificationType(type)
                    .title(title)
                    .body(body)
                    .isRead(false)
                    .build();
            notifications.add(notification); // 임시 저장
        }
        if (notifications.isEmpty()) {return;}
        notificationRepository.saveAll(notifications); // 레포 저장

// 커밋 성공 이후에만 FCM 발송이 실행되도록 등록
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (Notification notification : notifications) {
                            User receiver = notification.getReceiverUser();
                            if (receiver.getFcmToken() != null) {
                                fcmSender.send(receiver.getFcmToken(), notification.getTitle(), notification.getBody());
                            }
                        }
                    }
                }
        );
    }

    @Transactional(readOnly = true)
    public  boolean isEnabled(User receiver, NotificationType type) {
        return notificationSettingRepository.findById(receiver.getUsersId())
                .map(setting -> switch (type) {
                    case SOS -> setting.getSosEnabled();
                    case INACTIVITY -> setting.getInactivityEnabled();
                    case RISK_LINK -> setting.getRiskLinkEnabled();
                    case OUTING_RETURN -> setting.getOutingReturnEnabled();
                    default -> throw new BusinessException(ErrorCode.FORBIDDEN);
                })
                .orElse(true);
    }

    private NotificationType resolveType(EventType eventType) {
        return switch (eventType) {
            case SOS -> NotificationType.SOS;
            case INACTIVITY -> NotificationType.INACTIVITY;
            case RISK_LINK -> NotificationType.RISK_LINK;
            case OUTING_RETURN -> NotificationType.OUTING_RETURN;
        };
    }

    private String resolveTitle(EventType eventType) {
        return switch (eventType) {
            case SOS -> "SOS 알림";
            case INACTIVITY -> "무활동 감지 알림";
            case RISK_LINK -> "위험사이트 접속 알림";
            case OUTING_RETURN -> "외출·귀가 알림";
        };
    }

    private String resolveBody(EventType eventType, OutingPhase phase) {
        return switch (eventType) {
            case SOS -> "도움이 필요해요";
            case INACTIVITY -> "무활동 감지됨";
            case RISK_LINK -> "위험링크 감지됨";
            case OUTING_RETURN -> resolveOutingReturnMessage(phase);
            default -> "알림 감지";
        };
    }

    private String resolveOutingReturnMessage(OutingPhase phase) {
        if(phase == null) return "외출,귀가가 감지됨";
        return switch(phase){
            case OUTING -> "외출하셨어요";
            case RETURN -> "귀가하셨어요";
        };
    }

    // 디폴트 세팅 설정
    @Transactional
    public void createDefaultSetting(User user) {
        NotificationSetting setting = NotificationSetting.builder()
                .user(user)
                .build();

        notificationSettingRepository.save(setting);
    }

    //알람 탭 홈화면 조회
    @Transactional
    public List<NotificationHomeResponse> getHomeSettings(Long userId) {
        NotificationSetting setting = notificationSettingRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        return List.of(
                buildGroup(userId, NotificationType.SOS, setting.getSosEnabled()),
                buildGroup(userId, NotificationType.INACTIVITY, setting.getInactivityEnabled()),
                buildGroup(userId, NotificationType.RISK_LINK, setting.getRiskLinkEnabled()),
                buildGroup(userId, NotificationType.OUTING_RETURN, setting.getOutingReturnEnabled())

        );
    }

    private NotificationHomeResponse buildGroup(Long userId, NotificationType type, boolean enabled){
        Notification latest = notificationRepository.findLatestUnread(userId, type).orElse(null);
        return NotificationHomeResponse.of(type, enabled, latest);
    }

    // 알람 토글 설정
    @Transactional
    public NotificationSettingResponse updateSetting(
            Long userId,
            NotificationSettingType type,
            Boolean enabled
    ){
        if(enabled == null){
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        NotificationSetting setting = notificationSettingRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        switch (type) {
            case SOS -> setting.updateSosEnabled(enabled);
            case INACTIVITY -> setting.updateInactivityEnabled(enabled);
            case RISK_LINK -> setting.updateRiskLinkEnabled(enabled);
            case OUTING_RETURN -> setting.updateOutingReturnEnabled(enabled);
        }

        return NotificationSettingResponse.from(type, enabled);
    }
}

