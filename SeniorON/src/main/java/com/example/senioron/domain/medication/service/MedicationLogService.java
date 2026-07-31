package com.example.senioron.domain.medication.service;

import com.example.senioron.domain.medication.dto.response.MedicationCheckResponse;
import com.example.senioron.domain.medication.dto.response.MedicationMonthlyScheduleResponse;
import com.example.senioron.domain.medication.dto.response.MedicationScheduleResponse;
import com.example.senioron.domain.medication.dto.response.MedicationScheduleStatus;
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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final long MISSED_DELAY_MINUTES =
            30L;

    private static final int MEDICATION_LOG_CREATION_DAYS =
            30;

    private final MedicationLogRepository medicationLogRepository;
    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<MedicationScheduleResponse>
    getOwnDailyMedicationSchedules(
            Long requesterUserId,
            LocalDate date
    ) {
        User parentUser =
                getUserOrThrow(
                        requesterUserId
                );

        if (parentUser.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        return getDailyMedicationSchedules(
                parentUser,
                date
        );
    }

    public List<MedicationScheduleResponse>
    getParentDailyMedicationSchedules(
            Long requesterUserId,
            Long parentUserId,
            LocalDate date
    ) {
        User requester =
                getUserOrThrow(
                        requesterUserId
                );

        validateChild(
                requester
        );

        User parentUser =
                getSameFamilyParentOrThrow(
                        requester,
                        parentUserId
                );

        return getDailyMedicationSchedules(
                parentUser,
                date
        );
    }

    public MedicationMonthlyScheduleResponse
    getParentMonthlyMedicationSchedules(
            Long requesterUserId,
            Long parentUserId,
            Integer year,
            Integer month
    ) {
        User requester =
                getUserOrThrow(
                        requesterUserId
                );

        validateChild(
                requester
        );

        User parentUser =
                getSameFamilyParentOrThrow(
                        requester,
                        parentUserId
                );

        return getMonthlyMedicationSchedules(
                parentUser,
                year,
                month
        );
    }

    private List<MedicationScheduleResponse>
    getDailyMedicationSchedules(
            User parentUser,
            LocalDate date
    ) {
        List<MedicationLog> schedules =
                medicationLogRepository
                        .findByUserUsersIdAndPlannedDateOrderByPlannedTimeAsc(
                                parentUser.getUsersId(),
                                date
                        );

        List<MedicationLog> uniqueSchedules =
                deduplicateMedicationLogs(
                        schedules
                );

        LocalDateTime now =
                LocalDateTime.now(
                        KOREA_ZONE_ID
                );

        return uniqueSchedules.stream()
                .map(medicationLog ->
                        MedicationScheduleResponse.builder()
                                .medicationLogId(
                                        medicationLog.getMedicationLogId()
                                )
                                .medicineName(
                                        medicationLog.getMedication()
                                                .getMedicineName()
                                )
                                .ingredientName(
                                        medicationLog.getMedication()
                                                .getIngredientName()
                                )
                                .plannedDate(
                                        medicationLog.getPlannedDate()
                                )
                                .plannedTime(
                                        medicationLog.getPlannedTime()
                                )
                                .isTaken(
                                        medicationLog.getIsTaken()
                                )
                                .status(
                                        determineMedicationStatus(
                                                medicationLog,
                                                now
                                        )
                                )
                                .build()
                )
                .toList();
    }

    private MedicationMonthlyScheduleResponse
    getMonthlyMedicationSchedules(
            User parentUser,
            Integer year,
            Integer month
    ) {
        YearMonth yearMonth =
                createYearMonthOrThrow(
                        year,
                        month
                );

        LocalDate monthStartDate =
                yearMonth.atDay(1);

        LocalDate monthEndExclusiveDate =
                yearMonth.plusMonths(1)
                        .atDay(1);

        LocalDateTime monthStart =
                monthStartDate.atStartOfDay();

        LocalDateTime monthEndExclusive =
                monthEndExclusiveDate.atStartOfDay();

        List<Medication> medications =
                medicationRepository
                        .findEffectiveMedicationsForMonth(
                                parentUser,
                                monthStart,
                                monthEndExclusive
                        );

        List<LocalDate> scheduledDates =
                monthStartDate
                        .datesUntil(
                                monthEndExclusiveDate
                        )
                        .filter(date ->
                                medications.stream()
                                        .anyMatch(medication ->
                                                isScheduledForDate(
                                                        medication,
                                                        date
                                                )
                                        )
                        )
                        .toList();

        return new MedicationMonthlyScheduleResponse(
                yearMonth.getYear(),
                yearMonth.getMonthValue(),
                scheduledDates
        );
    }

    private YearMonth createYearMonthOrThrow(
            Integer year,
            Integer month
    ) {
        if (year == null
                || month == null
                || year < 1
                || month < 1
                || month > 12) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        try {
            return YearMonth.of(
                    year,
                    month
            );
        } catch (DateTimeException exception) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }
    }

    @Transactional
    public MedicationCheckResponse checkNearestMedication(
            Long requesterUserId
    ) {
        User parentUser =
                getUserOrThrow(
                        requesterUserId
                );

        if (parentUser.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        LocalDateTime now =
                LocalDateTime.now(
                        KOREA_ZONE_ID
                );

        LocalDate today =
                now.toLocalDate();

        LocalTime currentTime =
                now.toLocalTime();

        createMissingMedicationLogs(
                parentUser,
                today
        );

        List<MedicationLog> dailyMedicationLogs =
                medicationLogRepository
                        .findByUserUsersIdAndPlannedDateOrderByPlannedTimeAsc(
                                parentUser.getUsersId(),
                                today
                        );

        Map<String, MedicationLog> untakenMedicationLogs =
                new LinkedHashMap<>();

        Set<String> takenScheduleKeys =
                new HashSet<>();

        for (MedicationLog medicationLog :
                dailyMedicationLogs) {
            String scheduleKey =
                    createScheduleKey(
                            medicationLog.getMedication(),
                            medicationLog.getPlannedDate()
                    );

            if (Boolean.TRUE.equals(
                    medicationLog.getIsTaken()
            )) {
                takenScheduleKeys.add(
                        scheduleKey
                );

                untakenMedicationLogs.remove(
                        scheduleKey
                );

                continue;
            }

            if (takenScheduleKeys.contains(
                    scheduleKey
            )) {
                continue;
            }

            if (medicationLog.getPlannedTime()
                    .isAfter(
                            currentTime
                    )) {
                continue;
            }

            untakenMedicationLogs.putIfAbsent(
                    scheduleKey,
                    medicationLog
            );
        }

        MedicationLog nearestMedicationLog =
                untakenMedicationLogs.values()
                        .stream()
                        .max(
                                Comparator
                                        .comparing(
                                                MedicationLog::getPlannedTime
                                        )
                                        .thenComparing(
                                                MedicationLog::getMedicationLogId
                                        )
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.MEDICATION_NOT_FOUND
                                )
                        );

        return markMedicationAsTaken(
                nearestMedicationLog,
                now
        );
    }

    @Transactional
    public MedicationCheckResponse checkMedication(
            Long medicationLogId,
            Long requesterUserId
    ) {
        User parentUser =
                getUserOrThrow(
                        requesterUserId
                );

        if (parentUser.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        MedicationLog medicationLog =
                medicationLogRepository
                        .findById(
                                medicationLogId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.MEDICATION_NOT_FOUND
                                )
                        );

        if (!Objects.equals(
                medicationLog.getUser()
                        .getUsersId(),
                parentUser.getUsersId()
        )) {
            throw new BusinessException(
                    ErrorCode.MEDICATION_NOT_FOUND
            );
        }

        LocalDateTime now =
                LocalDateTime.now(
                        KOREA_ZONE_ID
                );

        LocalDateTime plannedAt =
                LocalDateTime.of(
                        medicationLog.getPlannedDate(),
                        medicationLog.getPlannedTime()
                );

        if (plannedAt.isAfter(
                now
        )) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        return markMedicationAsTaken(
                medicationLog,
                now
        );
    }

    private MedicationCheckResponse markMedicationAsTaken(
            MedicationLog medicationLog,
            LocalDateTime takenAt
    ) {
        if (Boolean.TRUE.equals(
                medicationLog.getIsTaken()
        )) {
            return MedicationCheckResponse.from(
                    medicationLog
            );
        }

        Long userId =
                medicationLog.getUser()
                        .getUsersId();

        String userName =
                medicationLog.getUser()
                        .getName();

        Long medicationLogId =
                medicationLog.getMedicationLogId();

        String medicineName =
                medicationLog.getMedication()
                        .getMedicineName();

        int updatedRows =
                medicationLogRepository
                        .markAsTakenIfUntaken(
                                medicationLogId,
                                takenAt
                        );

        if (updatedRows > 0) {
            eventPublisher.publishEvent(
                    new MedicationCheckedEvent(
                            userId,
                            userName,
                            medicationLogId,
                            medicineName
                    )
            );
        }

        MedicationLog updatedMedicationLog =
                medicationLogRepository
                        .findById(
                                medicationLogId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.MEDICATION_NOT_FOUND
                                )
                        );

        return MedicationCheckResponse.from(
                updatedMedicationLog
        );
    }

    @Transactional
    public void createMedicationLogsForNextThirtyDays(
            Long parentUserId
    ) {
        User parentUser =
                getUserOrThrow(
                        parentUserId
                );

        if (parentUser.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        LocalDate startDate =
                LocalDate.now(
                        KOREA_ZONE_ID
                );

        for (int dayOffset = 0;
             dayOffset < MEDICATION_LOG_CREATION_DAYS;
             dayOffset++) {
            LocalDate targetDate =
                    startDate.plusDays(
                            dayOffset
                    );

            createMissingMedicationLogs(
                    parentUser,
                    targetDate
            );
        }

        log.info(
                "30일 복약 로그 생성 완료. parentUserId: {}, startDate: {}, days: {}",
                parentUserId,
                startDate,
                MEDICATION_LOG_CREATION_DAYS
        );
    }

    @Transactional
    public void createTodayMedicationLogsForParent(
            Long parentUserId
    ) {
        User parentUser =
                getUserOrThrow(
                        parentUserId
                );

        if (parentUser.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        createMissingMedicationLogs(
                parentUser,
                LocalDate.now(
                        KOREA_ZONE_ID
                )
        );
    }

    @Transactional
    public void createTodayMedicationLogsForAllMedicationOwners() {
        LocalDate today =
                LocalDate.now(
                        KOREA_ZONE_ID
                );

        LocalDateTime dayStart =
                today.atStartOfDay();

        LocalDateTime dayEndExclusive =
                today.plusDays(1)
                        .atStartOfDay();

        List<Medication> medications =
                medicationRepository
                        .findAllEffectiveMedicationsForDate(
                                dayStart,
                                dayEndExclusive
                        );

        Map<Long, User> parentsById =
                medications.stream()
                        .map(
                                Medication::getUser
                        )
                        .filter(user ->
                                user.getRole() == Role.PARENT
                        )
                        .collect(
                                Collectors.toMap(
                                        User::getUsersId,
                                        user -> user,
                                        (existing, duplicate) ->
                                                existing,
                                        LinkedHashMap::new
                                )
                        );

        for (User parentUser :
                parentsById.values()) {
            createMissingMedicationLogs(
                    parentUser,
                    today
            );
        }

        log.info(
                "당일 복약 로그 자동 생성 완료. date: {}, parentCount: {}",
                today,
                parentsById.size()
        );
    }

    private void createMissingMedicationLogs(
            User parentUser,
            LocalDate date
    ) {
        LocalDateTime dayStart =
                date.atStartOfDay();

        LocalDateTime dayEndExclusive =
                date.plusDays(1)
                        .atStartOfDay();

        List<Medication> medications =
                medicationRepository
                        .findEffectiveMedicationsForDate(
                                parentUser,
                                dayStart,
                                dayEndExclusive
                        );

        List<MedicationLog> existingMedicationLogs =
                medicationLogRepository
                        .findByUserUsersIdAndPlannedDateOrderByPlannedTimeAsc(
                                parentUser.getUsersId(),
                                date
                        );

        Set<String> loggedScheduleKeys =
                existingMedicationLogs.stream()
                        .map(medicationLog ->
                                createScheduleKey(
                                        medicationLog.getMedication(),
                                        medicationLog.getPlannedDate()
                                )
                        )
                        .collect(
                                Collectors.toCollection(
                                        HashSet::new
                                )
                        );

        List<MedicationLog> newMedicationLogs =
                medications.stream()
                        .filter(medication ->
                                isScheduledForDate(
                                        medication,
                                        date
                                )
                        )
                        .filter(medication ->
                                loggedScheduleKeys.add(
                                        createScheduleKey(
                                                medication,
                                                date
                                        )
                                )
                        )
                        .map(medication ->
                                MedicationLog.builder()
                                        .user(
                                                parentUser
                                        )
                                        .medication(
                                                medication
                                        )
                                        .plannedDate(
                                                date
                                        )
                                        .plannedTime(
                                                medication.getMedicineTime()
                                        )
                                        .isTaken(
                                                false
                                        )
                                        .build()
                        )
                        .toList();

        if (!newMedicationLogs.isEmpty()) {
            medicationLogRepository.saveAll(
                    newMedicationLogs
            );

            log.info(
                    "복약 로그 생성 완료. parentUserId: {}, date: {}, count: {}",
                    parentUser.getUsersId(),
                    date,
                    newMedicationLogs.size()
            );
        }
    }

    private List<MedicationLog> deduplicateMedicationLogs(
            List<MedicationLog> medicationLogs
    ) {
        Map<String, MedicationLog> uniqueMedicationLogs =
                new LinkedHashMap<>();

        for (MedicationLog medicationLog :
                medicationLogs) {
            String scheduleKey =
                    createScheduleKey(
                            medicationLog.getMedication(),
                            medicationLog.getPlannedDate()
                    );

            uniqueMedicationLogs.merge(
                    scheduleKey,
                    medicationLog,
                    this::selectPreferredMedicationLog
            );
        }

        return uniqueMedicationLogs.values()
                .stream()
                .sorted(
                        Comparator
                                .comparing(
                                        MedicationLog::getPlannedTime
                                )
                                .thenComparing(
                                        MedicationLog::getMedicationLogId
                                )
                )
                .toList();
    }

    private MedicationLog selectPreferredMedicationLog(
            MedicationLog existingMedicationLog,
            MedicationLog candidateMedicationLog
    ) {
        boolean existingTaken =
                Boolean.TRUE.equals(
                        existingMedicationLog.getIsTaken()
                );

        boolean candidateTaken =
                Boolean.TRUE.equals(
                        candidateMedicationLog.getIsTaken()
                );

        if (candidateTaken && !existingTaken) {
            return candidateMedicationLog;
        }

        if (existingTaken && !candidateTaken) {
            return existingMedicationLog;
        }

        Long existingId =
                existingMedicationLog.getMedicationLogId();

        Long candidateId =
                candidateMedicationLog.getMedicationLogId();

        if (existingId == null) {
            return candidateMedicationLog;
        }

        if (candidateId == null) {
            return existingMedicationLog;
        }

        return candidateId < existingId
                ? candidateMedicationLog
                : existingMedicationLog;
    }

    private String createScheduleKey(
            Medication medication,
            LocalDate plannedDate
    ) {
        String medicationIdentifier;

        if (medication.getMedicationGroupId() != null
                && !medication.getMedicationGroupId()
                .isBlank()) {
            medicationIdentifier =
                    medication.getMedicationGroupId();
        } else {
            medicationIdentifier =
                    String.valueOf(
                            medication.getMedication_id()
                    );
        }

        return medicationIdentifier
                + "|"
                + plannedDate
                + "|"
                + medication.getMedicineTime();
    }

    private MedicationScheduleStatus determineMedicationStatus(
            MedicationLog medicationLog,
            LocalDateTime now
    ) {
        if (Boolean.TRUE.equals(
                medicationLog.getIsTaken()
        )) {
            return MedicationScheduleStatus.TAKEN;
        }

        LocalDateTime missedAt =
                LocalDateTime.of(
                                medicationLog.getPlannedDate(),
                                medicationLog.getPlannedTime()
                        )
                        .plusMinutes(
                                MISSED_DELAY_MINUTES
                        );

        if (!now.isBefore(
                missedAt
        )) {
            return MedicationScheduleStatus.MISSED;
        }

        return MedicationScheduleStatus.SCHEDULED;
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
                || medication.getMedicineDays()
                .isBlank()) {
            return false;
        }

        String targetDay =
                normalizeDay(
                        date.getDayOfWeek()
                                .name()
                );

        return Arrays.stream(
                        medication.getMedicineDays()
                                .split(",")
                )
                .map(
                        String::trim
                )
                .map(day ->
                        day.toUpperCase(
                                Locale.ROOT
                        )
                )
                .map(
                        this::normalizeDay
                )
                .anyMatch(day ->
                        Objects.equals(
                                day,
                                targetDay
                        )
                );
    }

    private void validateChild(
            User requester
    ) {
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
    }

    private User getSameFamilyParentOrThrow(
            User requester,
            Long parentUserId
    ) {
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
                        requester.getFamily()
                                .getFamilyId(),
                        parentUser.getFamily()
                                .getFamilyId()
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
        return userRepository
                .findById(
                        userId
                )
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