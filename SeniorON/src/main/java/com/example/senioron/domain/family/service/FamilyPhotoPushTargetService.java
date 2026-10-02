package com.example.senioron.domain.family.service;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.UserStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyPhotoPushTargetService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final FamilyPhotoGroupRepository familyPhotoGroupRepository;
    private final DeviceRepository deviceRepository;

    @Transactional(readOnly = true)
    public Optional<PushTarget> load(Long familyPhotoId) {
        var photo = familyPhotoRepository.findById(familyPhotoId)
                .orElse(null);

        if (photo == null) {
            log.info(
                    "[FamilyPhotoPush] 사진 없음. familyPhotoId={}",
                    familyPhotoId
            );
            return Optional.empty();
        }

        var parents = familyPhotoGroupRepository.findPhotoRecipientParents(
                familyPhotoId,
                Role.PARENT,
                UserStatus.ACTIVE
        );

        if (parents.isEmpty()) {
            log.info(
                    "[FamilyPhotoPush] 수신 대상 없음. familyPhotoId={}",
                    familyPhotoId
            );
            return Optional.empty();
        }

        List<String> tokens = deviceRepository.findAllByUserIn(parents)
                .stream()
                .filter(device ->
                        device.getConnectionStatus() != DeviceStatus.DISCONNECTED
                                && device.getConnectionStatus() != DeviceStatus.LOGIN_EXPIRED
                )
                .map(Device::getDeviceToken)
                .filter(token -> token != null && !token.isBlank())
                .distinct()
                .toList();

        if (tokens.isEmpty()) {
            log.warn(
                    "[FamilyPhotoPush] 수신 가능한 토큰 없음. familyPhotoId={}",
                    familyPhotoId
            );
            return Optional.empty();
        }

        String uploaderName = photo.getUser().getName();
        if (uploaderName == null || uploaderName.isBlank()) {
            uploaderName = "자녀";
        }

        return Optional.of(new PushTarget(
                uploaderName + "님이 새 가족사진을 공유했어요.",
                tokens
        ));
    }

    public record PushTarget(String body, List<String> tokens) {
    }
}