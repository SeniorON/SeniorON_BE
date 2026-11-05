package com.example.senioron.domain.family.service;

import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.permission.repository.SeniorPermissionSettingRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorReloginRequestRepository;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnusedFamilyCleanupService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final SeniorRepository seniorRepository;
    private final SeniorPermissionSettingRepository seniorPermissionSettingRepository;
    private final SeniorReloginRequestRepository seniorReloginRequestRepository;
    private final EventRepository eventRepository;
    private final FamilyPhotoSeniorRepository familyPhotoSeniorRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanup(Family family, List<Senior> lockedSeniors) {
        validateCleanupConditions(family, lockedSeniors);

        try {
            for (Senior senior : lockedSeniors) {
                seniorPermissionSettingRepository.deleteAllBySenior(senior);
            }
            seniorPermissionSettingRepository.flush();

            // Family는 Senior 삭제 시점까지 영속 상태이므로, 삭제 예정 Senior를
            // 역방향 연관관계에서 먼저 제거한다.
            family.unlinkSenior();
            seniorRepository.deleteAll(lockedSeniors);
            seniorRepository.flush();

            familyRepository.delete(family);
            familyRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw cleanupBlocked();
        }
    }

    private void validateCleanupConditions(
            Family family,
            List<Senior> lockedSeniors
    ) {
        if (family.getFirstSeniorConnectedAt() != null
                || familyMemberRepository.existsByFamily(family)) {
            throw cleanupBlocked();
        }

        boolean hasLinkedParent = lockedSeniors.stream()
                .anyMatch(senior -> senior.getParentUser() != null);
        if (hasLinkedParent) {
            throw cleanupBlocked();
        }

        boolean hasUsageData = lockedSeniors.stream()
                .anyMatch(this::hasUsageData);
        if (hasUsageData) {
            throw cleanupBlocked();
        }
    }

    private boolean hasUsageData(Senior senior) {
        return seniorReloginRequestRepository.existsBySenior(senior)
                || eventRepository.existsBySenior(senior)
                || familyPhotoSeniorRepository.existsBySenior(senior);
    }

    private BusinessException cleanupBlocked() {
        return new BusinessException(ErrorCode.UNUSED_FAMILY_CLEANUP_BLOCKED);
    }
}
