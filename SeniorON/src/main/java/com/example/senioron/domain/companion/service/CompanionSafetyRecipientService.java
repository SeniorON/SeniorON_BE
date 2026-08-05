package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionSafetyRecipientService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;

    public List<String> findChildTokens(
            Long parentUserId
    ) {
        User parent =
                userRepository.findById(
                        parentUserId
                ).orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        if (parent.getFamily() == null) {
            return List.of();
        }

        return deviceRepository
                .findAllByUser_FamilyAndUser_Role(
                        parent.getFamily(),
                        Role.CHILD
                )
                .stream()
                .map(device ->
                        device.getDeviceToken()
                )
                .filter(token ->
                        token != null
                                && !token.isBlank()
                )
                .distinct()
                .toList();
    }
}