package com.example.senioron.domain.medication.event;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MedicationEventListener {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final FcmSender fcmSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMedicationCheckedEvent(MedicationCheckedEvent event) {
        try {
            User parent = userRepository.findById(event.userId()).orElse(null);
            if (parent == null) return;

            Family family = parent.getFamily();

            if (family != null) {
                List<User> children = userRepository.findAllByFamily(family).stream()
                        .filter(user -> user.getRole() == Role.CHILD)
                        .collect(Collectors.toList());

                if (!children.isEmpty()) {
                    List<Device> devices = deviceRepository.findAllByUserIn(children);

                    for (Device device : devices) {
                        String token = device.getDeviceToken();
                        if (token != null && !token.isBlank()) {

                            log.info("📢 [FCM 전송 시도] 수신자 ID: {}, 디바이스 토큰: {}", parent.getUsersId(), maskToken(token));

                            fcmSender.send(
                                    token,
                                    "복약 알림",
                                    parent.getName() + "님이 약을 복용하셨습니다."
                            );

                            log.info(" [FCM 전송 완료] fcmSender.send() 성공적으로 호출됨!");
                        } else {
                            log.warn(" [FCM 전송 불가] 해당 유저의 디바이스 토큰이 비어있습니다.");
                        }
                    }
                } else {
                    log.info(" [FCM 미발송] 알림을 받을 자식 계정(Role.CHILD)이 존재하지 않습니다.");
                }
            } else {
                log.info(" [FCM 미발송] 속한 패밀리(Family)가 없습니다.");
            }
        } catch (Exception e) {
            log.error(" [FCM 이벤트 처리 중 에러 발생] userId: {}, error: {}", event.userId(), e.getMessage(), e);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "****";
        return token.substring(0, 8) + "...(masked)";
    }
}