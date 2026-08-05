package com.example.senioron.domain.medication.service;

import com.example.senioron.domain.medication.dto.request.MedicationCreateRequest;
import com.example.senioron.domain.medication.dto.request.MedicationUpdateRequest;
import com.example.senioron.domain.medication.dto.response.MedicationCreateResponse;
import com.example.senioron.domain.medication.dto.response.MedicationReadResponse;
import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.medication.entity.MedicationRepeatEndType;
import com.example.senioron.domain.medication.entity.MedicationRepeatType;
import com.example.senioron.domain.medication.repository.MedicationLogRepository;
import com.example.senioron.domain.medication.repository.MedicationRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationService {

    private static final ZoneId KOREA_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private static final List<String> DAY_ORDER =
            List.of(
                    "MON",
                    "TUE",
                    "WED",
                    "THU",
                    "FRI",
                    "SAT",
                    "SUN"
            );

    private static final Map<String, String> DAY_DISPLAY_NAMES =
            Map.ofEntries(
                    Map.entry("MON", "월"),
                    Map.entry("TUE", "화"),
                    Map.entry("WED", "수"),
                    Map.entry("THU", "목"),
                    Map.entry("FRI", "금"),
                    Map.entry("SAT", "토"),
                    Map.entry("SUN", "일")
            );

    private final MedicationRepository medicationRepository;
    private final MedicationLogRepository medicationLogRepository;
    private final UserRepository userRepository;
    private final MedicationLogService medicationLogService;

    @Transactional
    public MedicationCreateResponse createMedication(
            Long requesterUserId,
            Long parentUserId,
            MedicationCreateRequest request
    ) {
        User parentUser =
                getWritableParentOrThrow(
                        requesterUserId,
                        parentUserId
                );

        List<LocalTime> medicineTimes =
                parseAndValidateMedicineTimes(
                        request.getMedicineTimes()
                );

        LocalDate scheduleStartDate =
                parseRequiredDate(
                        request.getStartDate()
                );

        MedicationRepeatType repeatType =
                parseRepeatType(
                        request.getRepeatType()
                );

        Integer repeatInterval =
                validateRepeatInterval(
                        request.getRepeatInterval()
                );

        String medicineDays =
                resolveMedicineDays(
                        repeatType,
                        request.getMedicineDays()
                );

        MedicationRepeatEndType repeatEndType =
                parseRepeatEndType(
                        request.getRepeatEndType()
                );

        LocalDate scheduleEndDate =
                resolveScheduleEndDate(
                        scheduleStartDate,
                        repeatEndType,
                        request.getDurationWeeks(),
                        request.getEndDate()
                );

        Integer durationWeeks =
                repeatEndType == MedicationRepeatEndType.DURATION
                        ? request.getDurationWeeks()
                        : null;

        String medicationGroupId =
                UUID.randomUUID()
                        .toString();

        LocalDateTime effectiveFrom =
                LocalDateTime.now(
                        KOREA_ZONE_ID
                );

        List<Medication> medications =
                medicineTimes.stream()
                        .map(medicineTime ->
                                Medication.builder()
                                        .user(
                                                parentUser
                                        )
                                        .medicineName(
                                                request.getMedicineName()
                                        )
                                        .ingredientName(
                                                request.getIngredientName()
                                        )
                                        .medicineTime(
                                                medicineTime
                                        )
                                        .medicineDays(
                                                medicineDays
                                        )
                                        .medicationGroupId(
                                                medicationGroupId
                                        )
                                        .scheduleStartDate(
                                                scheduleStartDate
                                        )
                                        .scheduleEndDate(
                                                scheduleEndDate
                                        )
                                        .repeatType(
                                                repeatType
                                        )
                                        .repeatInterval(
                                                repeatInterval
                                        )
                                        .repeatEndType(
                                                repeatEndType
                                        )
                                        .durationWeeks(
                                                durationWeeks
                                        )
                                        .effectiveFrom(
                                                effectiveFrom
                                        )
                                        .effectiveTo(
                                                null
                                        )
                                        .build()
                        )
                        .toList();

        List<Medication> savedMedications =
                medicationRepository.saveAll(
                        medications
                );

        medicationRepository.flush();

        medicationLogService
                .createMedicationLogsForNextThirtyDays(
                        parentUser.getUsersId()
                );

        List<Long> medicationIds =
                savedMedications.stream()
                        .map(
                                Medication::getMedication_id
                        )
                        .toList();

        List<String> responseMedicineTimes =
                savedMedications.stream()
                        .map(
                                Medication::getMedicineTime
                        )
                        .map(
                                LocalTime::toString
                        )
                        .toList();

        log.info(
                "약 등록 완료. requesterUserId: {}, parentUserId: {}, groupId: {}",
                requesterUserId,
                parentUserId,
                medicationGroupId
        );

        return new MedicationCreateResponse(
                medicationIds,
                medicationGroupId,
                request.getMedicineName(),
                request.getIngredientName(),
                responseMedicineTimes,
                scheduleStartDate.toString(),
                repeatType.name(),
                repeatInterval,
                toDisplayDayList(
                        medicineDays
                ),
                repeatEndType.name(),
                durationWeeks,
                scheduleEndDate == null
                        ? null
                        : scheduleEndDate.toString()
        );
    }

    public List<MedicationReadResponse> getMedications(
            Long requesterUserId,
            Long parentUserId
    ) {
        User parentUser =
                getReadableParentOrThrow(
                        requesterUserId,
                        parentUserId
                );

        List<Medication> medications =
                medicationRepository
                        .findAllByUserAndEffectiveToIsNullOrderByMedicineTimeAsc(
                                parentUser
                        );

        Map<String, List<Medication>> medicationsByGroup =
                medications.stream()
                        .collect(
                                Collectors.groupingBy(
                                        this::createMedicationGroupKey,
                                        LinkedHashMap::new,
                                        Collectors.toList()
                                )
                        );

        return medicationsByGroup.values()
                .stream()
                .map(
                        this::toMedicationReadResponse
                )
                .toList();
    }

    @Transactional
    public void updateMedication(
            Long requesterUserId,
            Long parentUserId,
            MedicationUpdateRequest request
    ) {
        User parentUser =
                getWritableParentOrThrow(
                        requesterUserId,
                        parentUserId
                );

        List<Medication> existingMedications =
                medicationRepository
                        .findByUserAndMedicationGroupIdAndEffectiveToIsNull(
                                parentUser,
                                request.getMedicationGroupId()
                        );

        if (existingMedications.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.MEDICATION_NOT_FOUND
            );
        }

        List<LocalTime> medicineTimes =
                parseAndValidateMedicineTimes(
                        request.getMedicineTimes()
                );

        LocalDate scheduleStartDate =
                parseRequiredDate(
                        request.getStartDate()
                );

        MedicationRepeatType repeatType =
                parseRepeatType(
                        request.getRepeatType()
                );

        Integer repeatInterval =
                validateRepeatInterval(
                        request.getRepeatInterval()
                );

        String medicineDays =
                resolveMedicineDays(
                        repeatType,
                        request.getMedicineDays()
                );

        MedicationRepeatEndType repeatEndType =
                parseRepeatEndType(
                        request.getRepeatEndType()
                );

        LocalDate scheduleEndDate =
                resolveScheduleEndDate(
                        scheduleStartDate,
                        repeatEndType,
                        request.getDurationWeeks(),
                        request.getEndDate()
                );

        Integer durationWeeks =
                repeatEndType == MedicationRepeatEndType.DURATION
                        ? request.getDurationWeeks()
                        : null;

        LocalDateTime changedAt =
                LocalDateTime.now(
                        KOREA_ZONE_ID
                );

        existingMedications.forEach(
                medication ->
                        medication.endMedication(
                                changedAt
                        )
        );

        medicationLogRepository
                .deleteFutureUntakenLogs(
                        existingMedications,
                        changedAt.toLocalDate(),
                        changedAt.toLocalTime()
                );

        List<Medication> newMedications =
                medicineTimes.stream()
                        .map(medicineTime ->
                                Medication.builder()
                                        .user(
                                                parentUser
                                        )
                                        .medicineName(
                                                request.getMedicineName()
                                        )
                                        .ingredientName(
                                                request.getIngredientName()
                                        )
                                        .medicineTime(
                                                medicineTime
                                        )
                                        .medicineDays(
                                                medicineDays
                                        )
                                        .medicationGroupId(
                                                request.getMedicationGroupId()
                                        )
                                        .scheduleStartDate(
                                                scheduleStartDate
                                        )
                                        .scheduleEndDate(
                                                scheduleEndDate
                                        )
                                        .repeatType(
                                                repeatType
                                        )
                                        .repeatInterval(
                                                repeatInterval
                                        )
                                        .repeatEndType(
                                                repeatEndType
                                        )
                                        .durationWeeks(
                                                durationWeeks
                                        )
                                        .effectiveFrom(
                                                changedAt
                                        )
                                        .effectiveTo(
                                                null
                                        )
                                        .build()
                        )
                        .toList();

        medicationRepository.saveAll(
                newMedications
        );

        medicationRepository.flush();

        medicationLogService
                .createMedicationLogsForNextThirtyDays(
                        parentUser.getUsersId()
                );

        log.info(
                "약 수정 완료. requesterUserId: {}, parentUserId: {}, groupId: {}",
                requesterUserId,
                parentUserId,
                request.getMedicationGroupId()
        );
    }

    @Transactional
    public void deleteMedicationGroup(
            Long requesterUserId,
            Long parentUserId,
            String medicationGroupId
    ) {
        User parentUser =
                getWritableParentOrThrow(
                        requesterUserId,
                        parentUserId
                );

        List<Medication> medications =
                medicationRepository
                        .findByUserAndMedicationGroupIdAndEffectiveToIsNull(
                                parentUser,
                                medicationGroupId
                        );

        if (medications.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.MEDICATION_NOT_FOUND
            );
        }

        LocalDateTime deletedAt =
                LocalDateTime.now(
                        KOREA_ZONE_ID
                );

        medications.forEach(
                medication ->
                        medication.endMedication(
                                deletedAt
                        )
        );

        medicationLogRepository
                .deleteFutureUntakenLogs(
                        medications,
                        deletedAt.toLocalDate(),
                        deletedAt.toLocalTime()
                );

        log.info(
                "약 삭제 처리 완료. requesterUserId: {}, parentUserId: {}, groupId: {}",
                requesterUserId,
                parentUserId,
                medicationGroupId
        );
    }

    private MedicationReadResponse toMedicationReadResponse(
            List<Medication> medicationGroup
    ) {
        Medication representative =
                medicationGroup.get(0);

        List<Long> medicationIds =
                medicationGroup.stream()
                        .map(
                                Medication::getMedication_id
                        )
                        .toList();

        List<String> medicineTimes =
                medicationGroup.stream()
                        .map(
                                Medication::getMedicineTime
                        )
                        .map(
                                LocalTime::toString
                        )
                        .toList();

        LocalDate scheduleStartDate =
                representative.getScheduleStartDate() != null
                        ? representative.getScheduleStartDate()
                        : representative.getEffectiveFrom()
                        .toLocalDate();

        MedicationRepeatType repeatType =
                representative.getRepeatType() != null
                        ? representative.getRepeatType()
                        : MedicationRepeatType.WEEKLY;

        Integer repeatInterval =
                representative.getRepeatInterval() != null
                        ? representative.getRepeatInterval()
                        : 1;

        MedicationRepeatEndType repeatEndType =
                representative.getRepeatEndType() != null
                        ? representative.getRepeatEndType()
                        : MedicationRepeatEndType.ONGOING;

        return new MedicationReadResponse(
                medicationIds,
                representative.getMedicationGroupId(),
                representative.getMedicineName(),
                representative.getIngredientName(),
                medicineTimes,
                scheduleStartDate.toString(),
                repeatType.name(),
                repeatInterval,
                repeatType == MedicationRepeatType.WEEKLY
                        ? toDisplayDayList(
                        representative.getMedicineDays()
                )
                        : List.of(),
                repeatEndType.name(),
                representative.getDurationWeeks(),
                representative.getScheduleEndDate() == null
                        ? null
                        : representative.getScheduleEndDate()
                        .toString()
        );
    }

    private String createMedicationGroupKey(
            Medication medication
    ) {
        if (medication.getMedicationGroupId() != null
                && !medication.getMedicationGroupId()
                .isBlank()) {
            return medication.getMedicationGroupId();
        }

        return String.valueOf(
                medication.getMedication_id()
        );
    }

    private List<LocalTime> parseAndValidateMedicineTimes(
            List<String> medicineTimes
    ) {
        if (medicineTimes == null
                || medicineTimes.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        List<LocalTime> parsedMedicineTimes =
                medicineTimes.stream()
                        .map(
                                this::parseMedicineTime
                        )
                        .toList();

        Set<LocalTime> uniqueMedicineTimes =
                new LinkedHashSet<>(
                        parsedMedicineTimes
                );

        if (uniqueMedicineTimes.size()
                != parsedMedicineTimes.size()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        return parsedMedicineTimes;
    }

    private LocalDate parseRequiredDate(
            String date
    ) {
        if (date == null
                || date.isBlank()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        try {
            return LocalDate.parse(
                    date
            );
        } catch (DateTimeParseException exception) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }
    }

    private LocalDate parseOptionalDate(
            String date
    ) {
        if (date == null
                || date.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(
                    date
            );
        } catch (DateTimeParseException exception) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }
    }

    private MedicationRepeatType parseRepeatType(
            String repeatType
    ) {
        if (repeatType == null
                || repeatType.isBlank()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        try {
            return MedicationRepeatType.valueOf(
                    repeatType.trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }
    }

    private MedicationRepeatEndType parseRepeatEndType(
            String repeatEndType
    ) {
        if (repeatEndType == null
                || repeatEndType.isBlank()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        try {
            return MedicationRepeatEndType.valueOf(
                    repeatEndType.trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }
    }

    private Integer validateRepeatInterval(
            Integer repeatInterval
    ) {
        if (repeatInterval == null
                || repeatInterval < 1) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        return repeatInterval;
    }

    private String resolveMedicineDays(
            MedicationRepeatType repeatType,
            List<String> medicineDays
    ) {
        if (repeatType != MedicationRepeatType.WEEKLY) {
            return "";
        }

        if (medicineDays == null
                || medicineDays.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        return normalizeAndJoinDays(
                medicineDays
        );
    }

    private LocalDate resolveScheduleEndDate(
            LocalDate scheduleStartDate,
            MedicationRepeatEndType repeatEndType,
            Integer durationWeeks,
            String requestedEndDate
    ) {
        return switch (repeatEndType) {
            case ONGOING -> null;

            case DURATION -> {
                if (durationWeeks == null
                        || durationWeeks < 1) {
                    throw new BusinessException(
                            ErrorCode.BAD_REQUEST
                    );
                }

                yield scheduleStartDate
                        .plusWeeks(
                                durationWeeks
                        )
                        .minusDays(1);
            }

            case END_DATE -> {
                LocalDate scheduleEndDate =
                        parseOptionalDate(
                                requestedEndDate
                        );

                if (scheduleEndDate == null
                        || scheduleEndDate.isBefore(
                        scheduleStartDate
                )) {
                    throw new BusinessException(
                            ErrorCode.BAD_REQUEST
                    );
                }

                yield scheduleEndDate;
            }
        };
    }

    private String normalizeAndJoinDays(
            List<String> medicineDays
    ) {
        if (medicineDays == null
                || medicineDays.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        Set<String> normalizedDays =
                medicineDays.stream()
                        .filter(
                                Objects::nonNull
                        )
                        .map(
                                String::trim
                        )
                        .map(value ->
                                value.toUpperCase(
                                        Locale.ROOT
                                )
                        )
                        .map(
                                this::normalizeDay
                        )
                        .filter(
                                Objects::nonNull
                        )
                        .collect(
                                Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        );

        boolean containsInvalidDay =
                medicineDays.stream()
                        .anyMatch(day ->
                                day == null
                                        || normalizeDay(
                                        day.trim()
                                                .toUpperCase(
                                                        Locale.ROOT
                                                )
                                ) == null
                        );

        if (containsInvalidDay
                || normalizedDays.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        return DAY_ORDER.stream()
                .filter(
                        normalizedDays::contains
                )
                .collect(
                        Collectors.joining(",")
                );
    }

    private List<String> toDisplayDayList(
            String medicineDays
    ) {
        if (medicineDays == null
                || medicineDays.isBlank()) {
            return List.of();
        }

        Set<String> normalizedDays =
                Arrays.stream(
                                medicineDays.split(",")
                        )
                        .map(
                                String::trim
                        )
                        .map(value ->
                                value.toUpperCase(
                                        Locale.ROOT
                                )
                        )
                        .map(
                                this::normalizeDay
                        )
                        .filter(
                                Objects::nonNull
                        )
                        .collect(
                                Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        );

        return DAY_ORDER.stream()
                .filter(
                        normalizedDays::contains
                )
                .map(
                        DAY_DISPLAY_NAMES::get
                )
                .toList();
    }

    private LocalTime parseMedicineTime(
            String time
    ) {
        if (time == null
                || time.isBlank()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        try {
            return LocalTime.parse(
                    time
            );
        } catch (DateTimeParseException exception) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }
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

        validateChild(
                requester
        );

        return getSameFamilyParentOrThrow(
                requester,
                parentUserId
        );
    }

    private User getWritableParentOrThrow(
            Long requesterUserId,
            Long parentUserId
    ) {
        User requester =
                getUserOrThrow(
                        requesterUserId
                );

        validateChild(
                requester
        );

        return getSameFamilyParentOrThrow(
                requester,
                parentUserId
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