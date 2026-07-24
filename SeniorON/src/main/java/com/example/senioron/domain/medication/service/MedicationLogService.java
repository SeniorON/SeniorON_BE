package com.example.senioron.domain.medication.service;

import com.example.senioron.domain.medication.dto.response.MedicationCheckResponse;
import com.example.senioron.domain.medication.dto.response.MedicationScheduleResponse;
import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.medication.entity.MedicationLog;
import com.example.senioron.domain.medication.event.MedicationCheckedEvent;
import com.example.senioron.domain.medication.repository.MedicationLogRepository;
import com.example.senioron.domain.medication.repository.MedicationRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationLogService {

    private static final ZoneId KOREA_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private final MedicationLogRepository medicationLogRepository;
    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<MedicationScheduleResponse> getDailyMedicationSchedules(
            Long userId,
            LocalDate date
    ) {
        return getDailyMedicationSchedules(
                userId,
                userId,
                date
        );
    }

    @Transactional
    public List<MedicationScheduleResponse> getDailyMedicationSchedules(
            Long requesterUserId,
            Long parentUserId,
            LocalDate date
    ) {
        User parentUser = getReadableParentOrThrow(
                requesterUserId,
                parentUserId
        );

        createMissingMedicationLogs(
                parentUser,
                date
        );

        List<MedicationLog> schedules =
                medicationLogRepository
                        .findByUserUsersIdAndPlannedDateOrderByPlannedTimeAsc(
                                parentUser.getUsersId(),
                                date
                        );

        return schedules.stream()
                .map(log ->
                        MedicationScheduleResponse.builder()
                                .medicationLogId(
                                        log.getMedicationLogId()
                                )
                                .medicineName(
                                        log.getMedication()
                                                .getMedicineName()
                                )
                                .plannedTime(
                                        log.getPlannedTime()
                                )
                                .isTaken(
                                        log.getIsTaken()
                                )
                                .build()
                )
                .collect(Collectors.toList());
    }

    @Transactional
    public MedicationCheckResponse checkMedication(
            Long medicationLogId,
            User user
    ) {
        MedicationLog logEntity =
                medicationLogRepository
                        .findById(medicationLogId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.MEDICATION_NOT_FOUND
                                )
                        );

        getReadableParentOrThrow(
                user.getUsersId(),
                logEntity.getUser().getUsersId()
        );

        if (Boolean.TRUE.equals(
                logEntity.getIsTaken()
        )) {
            return MedicationCheckResponse.from(
                    logEntity
            );
        }

        LocalDateTime now =
                LocalDateTime.now(KOREA_ZONE_ID);

        int updatedRows =
                medicationLogRepository
                        .markAsTakenIfUntaken(
                                medicationLogId,
                                now
                        );

        if (updatedRows > 0) {
            eventPublisher.publishEvent(
                    new MedicationCheckedEvent(
                            logEntity.getUser().getUsersId(),
                            logEntity.getUser().getName()
                    )
            );
        }

        MedicationLog updatedLog =
                medicationLogRepository
                        .findById(medicationLogId)
                        .orElse(logEntity);

        return MedicationCheckResponse.from(
                updatedLog
        );
    }

    private void createMissingMedicationLogs(
            User parentUser,
            LocalDate date
    ) {
        LocalDateTime dayStart =
                date.atStartOfDay();

        LocalDateTime dayEndExclusive =
                date.plusDays(1).atStartOfDay();

        List<Medication> medications =
                medicationRepository
                        .findEffectiveMedicationsForDate(
                                parentUser,
                                dayStart,
                                dayEndExclusive
                        );

        List<MedicationLog> existingLogs =
                medicationLogRepository
                        .findByUserUsersIdAndPlannedDateOrderByPlannedTimeAsc(
                                parentUser.getUsersId(),
                                date
                        );

        Set<Long> loggedMedicationIds =
                new HashSet<>();

        existingLogs.forEach(log ->
                loggedMedicationIds.add(
                        log.getMedication()
                                .getMedication_id()
                )
        );

        List<MedicationLog> newLogs =
                medications.stream()
                        .filter(medication ->
                                isScheduledForDate(
                                        medication,
                                        date
                                )
                        )
                        .filter(medication ->
                                !loggedMedicationIds.contains(
                                        medication.getMedication_id()
                                )
                        )
                        .map(medication ->
                                MedicationLog.builder()
                                        .user(parentUser)
                                        .medication(medication)
                                        .plannedDate(date)
                                        .plannedTime(
                                                medication.getMedicineTime()
                                        )
                                        .isTaken(false)
                                        .build()
                        )
                        .toList();

        if (!newLogs.isEmpty()) {
            medicationLogRepository.saveAll(
                    newLogs
            );
        }
    }

    private boolean isScheduledForDate(
            Medication medication,
            LocalDate date
    ) {
        LocalDateTime scheduledAt =
                date.atTime(
                        medication.getMedicineTime()
                );

        if (medication.getEffectiveFrom() != null
                && scheduledAt.isBefore(
                medication.getEffectiveFrom()
        )) {
            return false;
        }

        if (medication.getEffectiveTo() != null
                && !scheduledAt.isBefore(
                medication.getEffectiveTo()
        )) {
            return false;
        }

        if (medication.getMedicineDays() == null
                || medication.getMedicineDays().isBlank()) {
            return false;
        }

        String targetDay =
                normalizeDay(
                        date.getDayOfWeek().name()
                );

        return Arrays.stream(
                        medication.getMedicineDays()
                                .split(",")
                )
                .map(String::trim)
                .map(day ->
                        day.toUpperCase(Locale.ROOT)
                )
                .map(this::normalizeDay)
                .anyMatch(day ->
                        Objects.equals(
                                day,
                                targetDay
                        )
                );
    }

    private User getReadableParentOrThrow(
            Long requesterUserId,
            Long parentUserId
    ) {
        User requester =
                getUserOrThrow(
                        requesterUserId
                );

        if (requester.getRole() == Role.PARENT) {
            if (!Objects.equals(
                    requester.getUsersId(),
                    parentUserId
            )) {
                throw new BusinessException(
                        ErrorCode.FORBIDDEN
                );
            }

            return requester;
        }

        if (requester.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        if (requester.getFamily() == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_FOUND
            );
        }

        User parentUser =
                getUserOrThrow(
                        parentUserId
                );

        if (parentUser.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.FAMILY_MEMBER_NOT_FOUND
            );
        }

        boolean belongsToSameFamily =
                parentUser.getFamily() != null
                        && Objects.equals(
                        requester.getFamily().getFamilyId(),
                        parentUser.getFamily().getFamilyId()
                );

        if (!belongsToSameFamily) {
            throw new BusinessException(
                    ErrorCode.FAMILY_MEMBER_NOT_FOUND
            );
        }

        return parentUser;
    }

    private User getUserOrThrow(
            Long userId
    ) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );
    }

    private String normalizeDay(
            String day
    ) {
        if (day == null) {
            return null;
        }

        return switch (day) {
            case "SUN", "SUNDAY", "일" -> "SUN";
            case "MON", "MONDAY", "월" -> "MON";
            case "TUE", "TUESDAY", "화" -> "TUE";
            case "WED", "WEDNESDAY", "수" -> "WED";
            case "THU", "THURSDAY", "목" -> "THU";
            case "FRI", "FRIDAY", "금" -> "FRI";
            case "SAT", "SATURDAY", "토" -> "SAT";
            default -> null;
        };
    }
}