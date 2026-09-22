package com.example.senioron.domain.permission.service;

import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.permission.dto.response.SeniorPermissionSettingResponse;
import com.example.senioron.domain.permission.entity.SeniorPermissionSetting;
import com.example.senioron.domain.permission.repository.SeniorPermissionSettingRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeniorPermissionSettingService {

    private final SeniorPermissionSettingRepository seniorPermissionSettingRepository;
    private final SeniorRepository seniorRepository;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;

    @Transactional
    public SeniorPermissionSettingResponse getSetting(User principal, Long seniorId) {
        User user = resolveCurrentUser(principal);
        Senior senior = seniorRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_NOT_FOUND));

        if (senior.getFamily() == null
                || !familyMemberRepository.existsByUserAndFamily(user, senior.getFamily())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        SeniorPermissionSetting setting = seniorPermissionSettingRepository.findById(senior.getSeniorId())
                .orElseGet(() -> createDefaultSetting(senior));
        return SeniorPermissionSettingResponse.from(setting);
    }

    private SeniorPermissionSetting createDefaultSetting(Senior senior) {
        try {
            SeniorPermissionSetting setting = SeniorPermissionSetting.builder()
                    .senior(senior)
                    .locationEnabled(true)
                    .inactivityDetectionEnabled(true)
                    .build();
            return seniorPermissionSettingRepository.saveAndFlush(setting);
        } catch (DataIntegrityViolationException e) {
            return seniorPermissionSettingRepository.findById(senior.getSeniorId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        }
    }

    private User resolveCurrentUser(User principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }
        return userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
