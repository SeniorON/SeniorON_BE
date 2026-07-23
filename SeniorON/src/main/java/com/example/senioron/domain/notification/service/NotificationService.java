package com.example.senioron.domain.notification.service;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.entity.OutingPhase;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.notification.dto.response.NotificationHomeResponse;
import com.example.senioron.domain.notification.dto.response.NotificationListResponse;
import com.example.senioron.domain.notification.dto.response.NotificationSettingResponse;
import com.example.senioron.domain.notification.dto.response.ParentDeviceStatusResponse;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
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
                    .linkUrl(event.getLinkUrl())
                    .isRead(false)
                    .build();
            notifications.add(notification); // 임시 저장
        }
        if (notifications.isEmpty()) {return;}
        notificationRepository.saveAll(notifications); // 레포 저장

        //수신자들의 기기 토큰을 미리 한 번에 조회
        Map<Long, List<String>> deviceTokensByUserId = deviceRepository.findAllByUserIn(receivers).stream()
                .filter(device -> device.getDeviceToken() != null && !device.getDeviceToken().isBlank())
                .collect(Collectors.groupingBy(
                        device -> device.getUser().getUsersId(),
                        Collectors.mapping(Device::getDeviceToken, Collectors.toList())
                ));

        // 커밋 성공 이후에만 FCM 발송이 실행되도록 등록
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (Notification notification : notifications) {
                            User receiver = notification.getReceiverUser();
                            List<String> tokens = deviceTokensByUserId.getOrDefault(receiver.getUsersId(), List.of());
                            for (String deviceToken : tokens) {
                                try {
                                    fcmSender.send(deviceToken, notification.getTitle(), notification.getBody());
                                } catch (Exception e) {
                                    log.warn("FCM 발송 처리 중 예외 발생, receiverId={}", receiver.getUsersId(), e);
                                }
                            }
                        }
                    }
                }
        );
    }

    @Transactional(readOnly = true)
    public  boolean isEnabled(User receiver, NotificationType type) {
        if (type == NotificationType.SOS) {
            return true; // SOS 알림은 필수 알림이라 끌 수 없음
        }
        return notificationSettingRepository.findById(receiver.getUsersId())
                .map(setting -> switch (type) {
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

    // 백필 대응 메소드 (기존 세팅 없을 경우 즉시 생성)
    private NotificationSetting createDefaultSettingInternal(User user){
        try{
            NotificationSetting setting = NotificationSetting.builder()
                    .user(user)
                    .build();
            return notificationSettingRepository.save(setting);
        } catch(DataIntegrityViolationException e){
            return notificationSettingRepository.findById(user.getUsersId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        }
    }
    //알람 탭 홈화면 조회
    @Transactional(readOnly = true)
    public List<NotificationHomeResponse> getHomeSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        NotificationSetting setting = notificationSettingRepository.findById(user.getUsersId())
                .orElseGet(() -> createDefaultSettingInternal(user));
        return List.of(
                buildGroup(userId, NotificationType.SOS, true), // SOS 알림은 필수 알림이라 항상 on으로 표시
                buildGroup(userId, NotificationType.INACTIVITY, setting.getInactivityEnabled()),
                buildGroup(userId, NotificationType.RISK_LINK, setting.getRiskLinkEnabled()),
                buildGroup(userId, NotificationType.OUTING_RETURN, setting.getOutingReturnEnabled())

        );
    }

    private NotificationHomeResponse buildGroup(Long userId, NotificationType type, boolean enabled) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(2);
        Notification latest = notificationRepository
                .findLatestUnread(userId, type, threshold)
                .orElse(null);
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateParentDeviceOnline(user);
        NotificationSetting setting = notificationSettingRepository.findById(user.getUsersId())
                .orElseGet(() -> createDefaultSettingInternal(user));

        switch (type) {
            case INACTIVITY -> setting.updateInactivityEnabled(enabled);
            case RISK_LINK -> setting.updateRiskLinkEnabled(enabled);
            case OUTING_RETURN -> setting.updateOutingReturnEnabled(enabled);
        }

        return NotificationSettingResponse.from(type, enabled);
    }

    // 자녀가 알림 설정을 변경하기 전, 같은 가족의 부모님 기기가 하나라도 오프라인이면 변경을 차단 (fail-safe)
    private void validateParentDeviceOnline(User child) {
        boolean anyOffline = findParentDeviceStatuses(child).stream()
                .anyMatch(status -> status == DeviceStatus.OFFLINE);
        if (anyOffline) {
            throw new BusinessException(ErrorCode.PARENT_DEVICE_OFFLINE);
        }
    }

    // 자녀가 부모님 기기의 온/오프라인 상태만 조회
    @Transactional(readOnly = true)
    public ParentDeviceStatusResponse getParentDeviceStatus(Long childUserId) {
        User child = userRepository.findById(childUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<DeviceStatus> statuses = findParentDeviceStatuses(child);
        boolean online = !statuses.isEmpty() && statuses.stream().allMatch(status -> status == DeviceStatus.ONLINE);

        return ParentDeviceStatusResponse.builder()
                .online(online)
                .build();
    }

    //매칭되는 모든 기기의 상태를 모아서 반환
    private List<DeviceStatus> findParentDeviceStatuses(User child) {
        if (child.getFamily() == null) {
            return List.of();
        }
        List<User> parents = userRepository.findByFamilyAndUsersIdNotAndRole(
                child.getFamily(), child.getUsersId(), Role.PARENT);
        if (parents.isEmpty()) {
            return List.of();
        }
        return deviceRepository.findAllByUserIn(parents).stream()
                .map(Device::getConnectionStatus)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationListResponse getNotificationList(Long userId, NotificationType type,Long cursor, int size) {
        if (size < 1 || size > 50) {throw new BusinessException(ErrorCode.BAD_REQUEST);}
        LocalDateTime thirtyDaysLimit = LocalDateTime.now().minusDays(30);
        List<Notification> notifications = notificationRepository
                .findByTypeWithCursor(userId, type, thirtyDaysLimit, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = notifications.size() > size;
        List<Notification> pageItems = hasNext ? notifications.subList(0, size) : notifications;
        Long nextCursor = hasNext ? pageItems.get(pageItems.size() - 1).getNotificationId() : null;
        long totalCount = notificationRepository.countByTypeWithin30Days(userId, type, thirtyDaysLimit);

        List<NotificationListResponse.NotificationItem> items = pageItems.stream()
                .map(n -> NotificationListResponse.NotificationItem.builder()
                        .notificationId(n.getNotificationId())
                        .eventId(n.getEvent().getEventId())
                        .title(n.getTitle())
                        .summary(n.getBody())
                        .occurredAt(n.getCreatedAt())
                        .isRead(n.getIsRead())
                        .build())
                .toList();

        return NotificationListResponse.builder()
                .totalCount(totalCount)
                .items(items)
                .nextCursor(nextCursor)
                .build();

    }
}

