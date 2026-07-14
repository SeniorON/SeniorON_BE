package com.example.senioron.domain.inactivity.service;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.inactivity.dto.request.InactivitySettingRequest;
import com.example.senioron.domain.inactivity.dto.response.InactivitySettingResponse;
import com.example.senioron.domain.inactivity.entity.InactivitySetting;
import com.example.senioron.domain.inactivity.repository.InactivitySettingRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class InactivitySettingService {

    private final InactivitySettingRepository inactivitySettingRepository;
    private final UserRepository userRepository;

    // 회원가입 시 디폴트 무활동 감지 설정 생성
    public void createDefaultSetting(User user) {
        InactivitySetting setting = InactivitySetting.builder()
                .user(user)
                .build();

        inactivitySettingRepository.save(setting);
    }
    //조회 시 세팅 없을 경우 생성
    private InactivitySetting createDefaultInternal(User targetUser) {
        InactivitySetting setting = InactivitySetting.builder()
                .user(targetUser)
                .build();
        return inactivitySettingRepository.save(setting);
    }

    @Transactional
    public InactivitySettingResponse getSetting(User principal, Long targetId) {
        User targetUser = resolveTargetUser(principal,targetId);

        InactivitySetting setting = inactivitySettingRepository.findById(targetUser.getUsersId())
                .orElseGet(() -> createDefaultInternal(targetUser));   // 없으면 즉석에서 생성
        return InactivitySettingResponse.from(setting);
    }

    // 가족 구성원(대상자)의 무활동 감지 설정 수정
    public InactivitySettingResponse updateSetting(User principal, Long targetUserId, InactivitySettingRequest request) {
        User targetUser = resolveTargetUser(principal, targetUserId);

        InactivitySetting setting = inactivitySettingRepository.findById(targetUser.getUsersId())
                .orElseGet(() -> createDefaultInternal(targetUser));

        setting.updateThresholdHours(request.getThresholdHours());
        return InactivitySettingResponse.from(setting);
    }

    // 요청자가 자식이고 대상자가 같은 가족인지 검증
    private User resolveTargetUser(User principal, Long targetUserId) {
        User currentUser = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Family family = currentUser.getFamily();
        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (targetUser.getRole() != Role.PARENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (targetUser.getFamily() == null || !Objects.equals(family.getFamilyId(), targetUser.getFamily().getFamilyId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return targetUser;
    }
}
