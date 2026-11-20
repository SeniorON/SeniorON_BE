package com.example.senioron.domain.senior.service;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;
import com.example.senioron.domain.senior.event.SeniorWithdrawalNoticeCreatedEvent;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalDataCleanupRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRecipientRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeniorWithdrawalService {

    private final SeniorRepository seniorRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final DeviceRepository deviceRepository;
    private final SeniorWithdrawalNoticeRepository noticeRepository;
    private final SeniorWithdrawalNoticeRecipientRepository recipientRepository;
    private final SeniorWithdrawalDataCleanupRepository cleanupRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${senior.withdrawal-notice.retention-days:30}")
    private long retentionDays;

    @Transactional(propagation = Propagation.MANDATORY)
    public void withdrawParent(User parentUser) {
        Optional<Senior> optionalSenior = seniorRepository.findByParentUserForUpdate(parentUser);
        LocalDateTime withdrawnAt = LocalDateTime.now();
        List<WithdrawalFamily> withdrawalFamilies = findWithdrawalFamilies(
                parentUser,
                optionalSenior.orElse(null)
        );
        List<SeniorWithdrawalNoticeCreatedEvent> events = new ArrayList<>();
        for (WithdrawalFamily withdrawalFamily : withdrawalFamilies) {
            List<FamilyMember> guardianMembers = findActiveGuardians(
                    withdrawalFamily.familyMembers()
            );
            SeniorWithdrawalNoticeCreatedEvent event = createNoticeEvent(
                    withdrawalFamily.senior(),
                    withdrawalFamily.family(),
                    guardianMembers,
                    parentUser.getName(),
                    withdrawnAt
            );
            if (event != null) {
                events.add(event);
            }
        }

        Senior senior = optionalSenior.orElse(null);
        cleanupRepository.deleteParentDependentData(parentUser, senior);

        if (senior != null) {
            WithdrawalFamily seniorFamily = withdrawalFamilies.stream()
                    .filter(withdrawalFamily -> withdrawalFamily.senior() != null)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND));

            familyMemberRepository.deleteAll(seniorFamily.familyMembers());
            familyMemberRepository.flush();
            seniorFamily.family().unlinkSenior();
            seniorRepository.delete(senior);
            seniorRepository.flush();
            familyRepository.delete(seniorFamily.family());
            familyRepository.flush();
        }

        for (SeniorWithdrawalNoticeCreatedEvent event : events) {
            eventPublisher.publishEvent(event);
        }
    }

    private List<WithdrawalFamily> findWithdrawalFamilies(User parentUser, Senior senior) {
        Set<Long> familyIds = new LinkedHashSet<>();
        if (senior != null) {
            familyIds.add(senior.getFamily().getFamilyId());
        }

        List<FamilyMember> parentMemberships = Optional.ofNullable(
                familyMemberRepository.findAllByUserOrderByIdAsc(parentUser)
        ).orElseGet(List::of);
        parentMemberships.stream()
                .map(FamilyMember::getFamily)
                .map(Family::getFamilyId)
                .forEach(familyIds::add);

        return familyIds.stream()
                .map(familyId -> familyRepository.findByIdForUpdate(familyId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND)))
                .map(family -> new WithdrawalFamily(
                        family,
                        senior != null && senior.getFamily().getFamilyId().equals(family.getFamilyId())
                                ? senior
                                : null,
                        familyMemberRepository.findAllByFamilyOrderByIdAsc(family)
                ))
                .toList();
    }

    private List<FamilyMember> findActiveGuardians(List<FamilyMember> familyMembers) {
        Map<Long, FamilyMember> uniqueGuardians = familyMembers.stream()
                .filter(member -> member.getUser().getRole() == Role.CHILD)
                .filter(member -> member.getUser().getStatus() == UserStatus.ACTIVE)
                .filter(member -> member.getManagerType() == ManagerType.PRIMARY
                        || member.getManagerType() == ManagerType.SUB)
                .collect(Collectors.toMap(
                        member -> member.getUser().getUsersId(),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        return List.copyOf(uniqueGuardians.values());
    }

    private SeniorWithdrawalNoticeCreatedEvent createNoticeEvent(
            Senior senior,
            Family family,
            List<FamilyMember> guardianMembers,
            String parentName,
            LocalDateTime withdrawnAt
    ) {
        if (senior != null && noticeRepository.findBySeniorId(senior.getSeniorId()).isPresent()) {
            return null;
        }

        SeniorWithdrawalNotice notice = SeniorWithdrawalNotice.builder()
                .seniorId(senior == null ? null : senior.getSeniorId())
                .familyId(family.getFamilyId())
                .seniorName(senior == null ? parentName : senior.getName())
                .withdrawnAt(withdrawnAt)
                .expiresAt(withdrawnAt.plusDays(retentionDays))
                .completedAt(guardianMembers.isEmpty() ? withdrawnAt : null)
                .build();
        SeniorWithdrawalNotice savedNotice = noticeRepository.saveAndFlush(notice);

        if (!guardianMembers.isEmpty()) {
            List<SeniorWithdrawalNoticeRecipient> recipients = guardianMembers.stream()
                    .map(member -> SeniorWithdrawalNoticeRecipient.builder()
                            .notice(savedNotice)
                            .recipientUser(member.getUser())
                            .relation(member.getRelation())
                            .customRelation(member.getCustomRelation())
                            .build())
                    .toList();
            recipientRepository.saveAllAndFlush(recipients);
        }

        List<User> guardians = guardianMembers.stream()
                .map(FamilyMember::getUser)
                .toList();
        Map<Long, List<String>> tokensByUserId = loadTokensByUserId(guardians);
        List<SeniorWithdrawalNoticeCreatedEvent.PushTarget> targets = guardians.stream()
                .map(guardian -> new SeniorWithdrawalNoticeCreatedEvent.PushTarget(
                        guardian.getUsersId(),
                        tokensByUserId.getOrDefault(guardian.getUsersId(), List.of())
                ))
                .toList();

        return new SeniorWithdrawalNoticeCreatedEvent(
                savedNotice.getSeniorWithdrawalNoticeId(),
                senior == null ? null : senior.getSeniorId(),
                family.getFamilyId(),
                senior == null ? parentName : senior.getName(),
                targets
        );
    }

    private record WithdrawalFamily(
            Family family,
            Senior senior,
            List<FamilyMember> familyMembers
    ) {
    }

    private Map<Long, List<String>> loadTokensByUserId(List<User> guardians) {
        if (guardians.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> tokensByUserId = new LinkedHashMap<>();
        for (Device device : deviceRepository.findAllByUserIn(guardians)) {
            if (device.getUser() == null
                    || device.getDeviceToken() == null
                    || device.getDeviceToken().isBlank()) {
                continue;
            }

            tokensByUserId.computeIfAbsent(
                    device.getUser().getUsersId(),
                    ignored -> new ArrayList<>()
            ).add(device.getDeviceToken());
        }

        return tokensByUserId.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().distinct().toList(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }
}
