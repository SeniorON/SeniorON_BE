package com.example.senioron.domain.inactivity.service;

import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InactivitySettingService {

    private final InactivitySettingRepository inactivitySettingRepository;
    private final UserRepository userRepository;
    private final SeniorRepository seniorRepository;
    private final FamilyMemberRepository familyMemberRepository;

    // 회원가입 시 디폴트 무활동 감지 설정 생성
    public void createDefaultSetting(User user) {
        InactivitySetting setting = InactivitySetting.builder()
                .user(user)
                .build();

        inactivitySettingRepository.save(setting);
    }
    //조회 시 세팅 없을 경우 생성
    private InactivitySetting createDefaultInternal(User targetUser) {
        try {
            InactivitySetting setting = InactivitySetting.builder()
                    .user(targetUser)
                    .isEnabled(true)
                    .thresholdHours(4)
                    .build();
            return inactivitySettingRepository.saveAndFlush(setting);
        } catch (DataIntegrityViolationException e) {
            // 동시에 다른 요청이 먼저 만들어버린 경우 그냥 다시 조회 후 반환
            return inactivitySettingRepository.findById(targetUser.getUsersId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INACTIVITY_SETTING_NOT_FOUND));
        }
    }

    @Transactional
    public InactivitySettingResponse getSetting(User principal, Long targetUserId) {
        User targetUser = resolveTargetUser(principal, targetUserId);

        InactivitySetting setting = inactivitySettingRepository.findById(targetUser.getUsersId())
                .orElseGet(() -> createDefaultInternal(targetUser));   // 없으면 즉석에서 생성
        return InactivitySettingResponse.from(setting);
    }

    // 부모(시니어) 기기가 폴링으로 자신의 무활동 감지 설정을 직접 조회
    @Transactional
    public InactivitySettingResponse getMySetting(User principal) {
        User currentUser = resolveCurrentUser(principal);
        if (currentUser.getRole() != Role.PARENT) {
            throw new BusinessException(ErrorCode.INACTIVITY_SETTING_PARENT_ONLY);
        }

        InactivitySetting setting = inactivitySettingRepository.findById(currentUser.getUsersId())
                .orElseGet(() -> createDefaultInternal(currentUser));
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

    // 경로 ID는 부모 usersId이며, 해당 부모와 연결된 Senior의 가족 멤버십으로 검증한다.
    private User resolveTargetUser(User principal, Long targetUserId) {
        User currentUser = resolveCurrentUser(principal);
        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (targetUser.getRole() != Role.PARENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Senior senior = seniorRepository.findByParentUser(targetUser)
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_NOT_FOUND));
        if (senior.getFamily() == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }
        if (!familyMemberRepository.existsByUserAndFamily(currentUser, senior.getFamily())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return targetUser;
    }

    private User resolveCurrentUser(User principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }
        return userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
