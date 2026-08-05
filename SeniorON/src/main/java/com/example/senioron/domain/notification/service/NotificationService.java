package com.example.senioron.domain.notification.service;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.entity.OutingPhase;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.notification.dto.NotificationDispatchResult;
import com.example.senioron.domain.notification.dto.NotificationDispatchTarget;
import com.example.senioron.domain.notification.dto.response.NotificationHomeListResponse;
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
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    // SOS는 미발송 자체가 사고이므로 별도로 관리
    private static final String SOS_DISPATCH_METRIC = "sos_dispatch_total";
    private static final String TAG_RESULT = "result";
    private static final int SOS_DISPATCH_POOL_SIZE = 8;
    private static final int GENERAL_DISPATCH_POOL_SIZE = 8;

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final FcmSender fcmSender;
    private final MeterRegistry meterRegistry;
    // SOS는 일반 알림 발송 적체(backlog)에 영향받지 않도록 별도 풀에서 처리한다.
    private final ExecutorService sosDispatchExecutor = Executors.newFixedThreadPool(SOS_DISPATCH_POOL_SIZE);
    private final ExecutorService generalDispatchExecutor = Executors.newFixedThreadPool(GENERAL_DISPATCH_POOL_SIZE);

    @PreDestroy
    void shutdownDispatchExecutors() {
        shutdownExecutor(sosDispatchExecutor);
        shutdownExecutor(generalDispatchExecutor);
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 일반 알림. 알림을 저장하고 커밋 이후 비동기로 발송한다.
     * 발송 실패는 API 응답에 로그로만 남김.
     */
    @Transactional
    public void createFormEvent(Event event) {
        List<NotificationDispatchTarget> targets = prepareNotifications(event);
        if (targets.isEmpty()) return;

        // 커밋 성공 이후에만 FCM 발송이 실행되도록 등록
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatch(targets);
                    }
                }
        );
    }

    /**
     * SOS 전용. 알림 저장까지만 하고 발송 대상을 돌려준다
     * 발송은 호출부가 커밋 이후 {@link #dispatchSos}로 수행해, 그 결과를 응답에 담을 수 있게 한다.
     */
    @Transactional
    public List<NotificationDispatchTarget> prepareSosNotifications(Event event) {
        return prepareNotifications(event);
    }

    /**
     * SOS 알림을 동기 발송한다.
     */
    public NotificationDispatchResult dispatchSos(List<NotificationDispatchTarget> targets) {
        if (targets.isEmpty()) {
            // 가족이나 자녀가 등록되지 않아 SOS를 알릴 대상 자체가 없는 경우.
            countSosDispatch("no_receiver");
            log.error("SOS 수신 대상이 없어 알림을 발송하지 못했습니다.");
            return NotificationDispatchResult.noReceiver();
        }

        NotificationDispatchResult result = dispatchSosInParallel(targets);

        if (result.notifiedCount() == 0) {
            countSosDispatch("undelivered");
            log.error("SOS 알림을 아무에게도 발송하지 못했습니다. receiverCount={}", result.receiverCount());
        } else if (!result.isFullyDelivered()) {
            countSosDispatch("partial");
            log.warn("SOS 알림 일부만 발송되었습니다. notified={}/{}",
                    result.notifiedCount(), result.receiverCount());
        } else {
            countSosDispatch("delivered");
        }

        return result;
    }

    /**
     * 알림을 저장하고, 커밋 이후 발송에 필요한 정보를 추출한다.
     */
    private List<NotificationDispatchTarget> prepareNotifications(Event event) {
        User sender = event.getTriggeredUser();
        if(sender.getFamily() == null) {
            log.warn("가족이 등록되지 않아 알림 대상이 없습니다. eventType={}, senderId={}",
                    event.getEventType(), sender.getUsersId());
            return List.of();
        }

        List<User> receivers = userRepository.findByFamilyAndUsersIdNotAndRole(sender.getFamily(), sender.getUsersId(), Role.CHILD);
        if (receivers.isEmpty()) {
            log.warn("수신 가능한 자녀가 없어 알림 대상이 없습니다. eventType={}, senderId={}",
                    event.getEventType(), sender.getUsersId());
            return List.of();
        }

        NotificationType type = resolveType(event.getEventType());

        // receivers는 전부 sender와 같은 가족의 자녀라 알림 설정을 공유하는 시니어가 동일하다.
        // 수신자마다 반복 조회하지 않고 한 번만 확인한다.
        if (!isEnabled(receivers.get(0), type)) {
            return List.of();
        }

        String title = resolveTitle(event.getEventType());
        String body = resolveBody(event);
        List<Notification> notifications = receivers.stream()
                .map(receiver -> Notification.builder()
                        .event(event)
                        .sendUser(sender)
                        .receiverUser(receiver)
                        .notificationType(type)
                        .title(title)
                        .body(body)
                        .linkUrl(event.getLinkUrl())
                        .isRead(false)
                        .build())
                .toList();
        notificationRepository.saveAll(notifications); // 레포 저장

        //수신자들의 기기 토큰을 미리 한 번에 조회
        Map<Long, List<String>> deviceTokensByUserId = deviceRepository.findAllByUserIn(receivers).stream()
                .filter(device -> device.getDeviceToken() != null && !device.getDeviceToken().isBlank())
                .collect(Collectors.groupingBy(
                        device -> device.getUser().getUsersId(),
                        Collectors.mapping(Device::getDeviceToken, Collectors.toList())
                ));

        return notifications.stream()
                .map(notification -> {
                    Long receiverId = notification.getReceiverUser().getUsersId();
                    return new NotificationDispatchTarget(
                            receiverId,
                            notification.getTitle(),
                            notification.getBody(),
                            deviceTokensByUserId.getOrDefault(receiverId, List.of())
                    );
                })
                .toList();
    }

    /**
     * 수신자별로 등록된 모든 기기에 발송한다. afterCommit 콜백에서 호출되므로 요청 스레드를
     * 블로킹하지 않도록 각 발송을 generalDispatchExecutor에 위임하고 결과를 기다리지 않는다.
     */
    private void dispatch(List<NotificationDispatchTarget> targets) {
        for (NotificationDispatchTarget target : targets) {
            generalDispatchExecutor.execute(() -> sendToAnyDevice(target));
        }
    }

    //병렬발송 (일반 알림 적체와 무관하게 처리되도록 전용 풀 사용)
    private NotificationDispatchResult dispatchSosInParallel(List<NotificationDispatchTarget> targets) {
        long timeoutSeconds = 5L;
        List<CompletableFuture<Boolean>> futures = targets.stream()
                .map(target -> CompletableFuture.supplyAsync(
                        () -> sendToAnyDevice(target), sosDispatchExecutor)
                        .completeOnTimeout(false, timeoutSeconds, TimeUnit.SECONDS))
                .toList();

        long notifiedCount = futures.stream()
                .map(CompletableFuture::join)
                .filter(Boolean::booleanValue)
                .count();

        return new NotificationDispatchResult(targets.size(), (int) notifiedCount);
    }

    private boolean sendToAnyDevice(NotificationDispatchTarget target) {
        boolean delivered = false;
        for (String deviceToken : target.deviceTokens()) {
            try {
                boolean sent = fcmSender.send(deviceToken, target.title(), target.body());
                delivered = delivered || sent;
            } catch (Exception e) {
                log.warn("FCM 발송 처리 중 예외 발생, receiverId={}", target.receiverId(), e);
            }
        }
        return delivered;
    }

    private void countSosDispatch(String result) {
        meterRegistry.counter(SOS_DISPATCH_METRIC, TAG_RESULT, result).increment();
    }

    @Transactional(readOnly = true)
    public  boolean isEnabled(User receiver, NotificationType type) {
        if (type == NotificationType.SOS) {
            return true; // SOS 알림은 필수 알림이라 끌 수 없음
        }
        return resolveSeniorOwner(receiver)
                .flatMap(senior -> notificationSettingRepository.findById(senior.getUsersId()))
                .map(setting -> switch (type) {
                    case INACTIVITY -> setting.getInactivityEnabled();
                    case RISK_LINK -> setting.getRiskLinkEnabled();
                    case OUTING_RETURN -> setting.getOutingReturnEnabled();
                    default -> throw new BusinessException(ErrorCode.FORBIDDEN);
                })
                .orElse(true);
    }

    //알림 설정은 유저 개인이 아니라 가족의 시니어(PARENT) 기준으로 공유
    //본인이 PARENT면 자기 자신, CHILD면 같은 가족의 PARENT를 반환
    private Optional<User> resolveSeniorOwner(User user) {
        if (user.getRole() == Role.PARENT) {
            return Optional.of(user);
        }
        if (user.getFamily() == null) {
            return Optional.empty();
        }
        // 부모가 2명 이상이면 usersId가 가장 작은 한 명으로 고정 (쿼리에 ORDER BY u.usersId ASC 있음)
        return userRepository.findByFamilyAndUsersIdNotAndRole(
                        user.getFamily(), user.getUsersId(), Role.PARENT)
                .stream()
                .findFirst();
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

    private String resolveBody(Event event) {
        return switch (event.getEventType()) {
            case SOS -> "도움이 필요해요";
            case INACTIVITY -> resolveInactivityMessage(event.getLastSeenAt(), event.getCreatedAt());
            case RISK_LINK -> "위험링크 감지됨";
            case OUTING_RETURN -> resolveOutingReturnMessage(event.getPhase());
        };
    }

    // "4시간 미사용 감지됨" — 마지막 활동 시각부터 감지 시각까지 경과 시간
    private String resolveInactivityMessage(LocalDateTime lastSeenAt, LocalDateTime detectedAt) {
        if (lastSeenAt == null || detectedAt == null) {
            return "무활동 감지됨";
        }
        long hours = Duration.between(lastSeenAt, detectedAt).toHours();
        return hours + "시간 미사용 감지됨";
    }

    private String resolveOutingReturnMessage(OutingPhase phase) {
        if(phase == null) return "외출,귀가가 감지됨";
        return switch(phase){
            case OUTING -> "외출하셨어요";
            case RETURN -> "귀가하셨어요";
        };
    }

    // 백필 대응 메소드 (기존 세팅 없을 경우 즉시 생성)
    private NotificationSetting createDefaultSettingInternal(User user){
        try{
            NotificationSetting setting = NotificationSetting.builder()
                    .user(user)
                    .build();
            // save()는 INSERT를 즉시 실행하지 않고 다음 쿼리의 자동 flush 시점까지 미룰 수 있다.
            // 그러면 DB 제약조건 위반(예: 스키마 드리프트로 남은 컬럼의 NOT NULL)이 이 메서드 밖,
            // 즉 이 catch가 못 잡는 시점에 터진다. saveAndFlush로 즉시 실행시켜 여기서 확실히 잡는다.
            return notificationSettingRepository.saveAndFlush(setting);
        } catch(DataIntegrityViolationException e){
            return notificationSettingRepository.findById(user.getUsersId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        }
    }
    //알람 탭 홈화면 조회
    @Transactional
    public NotificationHomeListResponse getHomeSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User senior = resolveSeniorOwner(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND));
        NotificationSetting setting = notificationSettingRepository.findById(senior.getUsersId())
                .orElseGet(() -> createDefaultSettingInternal(senior));
        List<NotificationHomeResponse> items = List.of(
                buildGroup(userId, NotificationType.SOS, true), // SOS 알림은 필수 알림이라 항상 on으로 표시
                buildGroup(userId, NotificationType.INACTIVITY, setting.getInactivityEnabled()),
                buildGroup(userId, NotificationType.RISK_LINK, setting.getRiskLinkEnabled()),
                buildGroup(userId, NotificationType.OUTING_RETURN, setting.getOutingReturnEnabled())
        );
        return NotificationHomeListResponse.of(items);
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

        User senior;
        if (user.getRole() == Role.PARENT) {
            senior = user;
        } else {
            if (user.getFamily() == null) {
                throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
            }
            List<User> parents = userRepository.findByFamilyAndUsersIdNotAndRole(
                    user.getFamily(), user.getUsersId(), Role.PARENT);
            if (parents.isEmpty()) {
                throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
            }
            if (!areAllDevicesOnline(findParentDeviceStatuses(parents))) {
                throw new BusinessException(ErrorCode.PARENT_DEVICE_OFFLINE);
            }
            senior = parents.get(0);
        }

        NotificationSetting setting = notificationSettingRepository.findById(senior.getUsersId())
                .orElseGet(() -> createDefaultSettingInternal(senior));

        switch (type) {
            case INACTIVITY -> setting.updateInactivityEnabled(enabled);
            case RISK_LINK -> setting.updateRiskLinkEnabled(enabled);
            case OUTING_RETURN -> setting.updateOutingReturnEnabled(enabled);
        }

        return NotificationSettingResponse.from(type, enabled);
    }

    // 자녀가 알림 설정을 변경하기 전, 같은 가족의 부모님 기기가 하나라도 오프라인이면 변경을 차단 (fail-safe)
    private void validateParentDeviceOnline(User child) {
        if (!areAllDevicesOnline(findParentDeviceStatuses(child))) {
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
        return findParentDeviceStatuses(parents);
    }

    private List<DeviceStatus> findParentDeviceStatuses(List<User> parents) {
        if (parents == null || parents.isEmpty()) {
            return List.of();
        }
        return deviceRepository.findAllByUserIn(parents).stream()
                .map(Device::getConnectionStatus)
                .toList();
    }

    private boolean areAllDevicesOnline(List<DeviceStatus> statuses) {
        return !statuses.isEmpty()
                && statuses.stream().allMatch(status -> status == DeviceStatus.ONLINE);
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

    // 알림 읽음 처리
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = findOwnedNotification(userId, notificationId);

        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return;
        }
        notification.markAsRead();
    }

    // 알림 삭제
    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        Notification notification = findOwnedNotification(userId, notificationId);
        notificationRepository.delete(notification);
    }

    // 본인이 받은 알림인지 검증 후 반환 (아니면 404/403)
    private Notification findOwnedNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getReceiverUser().getUsersId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return notification;
    }

    // 생성된지 30일 지난 알림 일괄 삭제
    @Transactional
    public void deleteOldNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int deletedCount = notificationRepository.deleteAllByCreatedAtBefore(threshold);
        log.info("[알림 정리 배치] 30일 경과 알림 {}건 삭제", deletedCount);
    }
}
