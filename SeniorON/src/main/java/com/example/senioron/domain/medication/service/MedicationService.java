package com.example.senioron.domain.medication.service;

import com.example.senioron.domain.home.service.HomeWebSocketService;
import com.example.senioron.domain.medication.dto.request.MedicationCreateRequest;
import com.example.senioron.domain.medication.dto.request.MedicationUpdateRequest;
import com.example.senioron.domain.medication.dto.response.MedicationCreateResponse;
import com.example.senioron.domain.medication.dto.response.MedicationReadResponse;
import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.medication.entity.MedicationRepeatEndType;
import com.example.senioron.domain.medication.entity.MedicationRepeatType;
import com.example.senioron.domain.medication.repository.MedicationLogRepository;
import com.example.senioron.domain.medication.repository.MedicationRepository;
import com.example.senioron.domain.medication.support.MedicationFamilyAuthorization;
import com.example.senioron.domain.medication.support.MedicationWeekdayUtils;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
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

    private final MedicationRepository medicationRepository;
    private final MedicationLogRepository medicationLogRepository;
    private final MedicationLogService medicationLogService;
    private final MedicationFamilyAuthorization medicationFamilyAuthorization;
    private final HomeWebSocketService homeWebSocketService;

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
                        repeatType,
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

        homeWebSocketService.notifyHomeUpdated(
                parentUser.getUsersId()
        );

        homeWebSocketService.notifyHomeUpdated(
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
                MedicationWeekdayUtils.toDisplayDayList(
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
                        repeatType,
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

        homeWebSocketService.notifyHomeUpdated(
                parentUser.getUsersId()
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
                        : resolveLegacyScheduleStartDate(
                        representative
                );

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

        List<String> medicineDayList =
                repeatType == MedicationRepeatType.WEEKLY
                        ? MedicationWeekdayUtils.toDisplayDayList(
                        representative.getMedicineDays()
                )
                        : List.of();

        return MedicationReadResponse.builder()
                .medicationId(
                        representative.getMedication_id()
                )
                .medicationIds(
                        medicationIds
                )
                .medicationGroupId(
                        representative.getMedicationGroupId()
                )
                .medicineName(
                        representative.getMedicineName()
                )
                .ingredientName(
                        representative.getIngredientName()
                )
                .medicineTime(
                        formatMedicineTime(
                                representative.getMedicineTime()
                        )
                )
                .medicineTimes(
                        medicineTimes
                )
                .startDate(
                        scheduleStartDate == null
                                ? null
                                : scheduleStartDate.toString()
                )
                .repeatType(
                        repeatType.name()
                )
                .repeatInterval(
                        repeatInterval
                )
                .medicineDays(
                        MedicationWeekdayUtils.formatMedicineDays(
                                representative.getMedicineDays()
                        )
                )
                .medicineDayList(
                        medicineDayList
                )
                .repeatEndType(
                        repeatEndType.name()
                )
                .durationWeeks(
                        representative.getDurationWeeks()
                )
                .endDate(
                        representative.getScheduleEndDate() == null
                                ? null
                                : representative.getScheduleEndDate()
                                .toString()
                )
                .build();
    }

    private LocalDate resolveLegacyScheduleStartDate(
            Medication medication
    ) {
        if (medication.getEffectiveFrom() == null) {
            return null;
        }

        return medication.getEffectiveFrom()
                .toLocalDate();
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

        return MedicationWeekdayUtils.normalizeAndJoinDays(
                medicineDays
        );
    }

    private LocalDate resolveScheduleEndDate(
            LocalDate scheduleStartDate,
            MedicationRepeatType repeatType,
            MedicationRepeatEndType repeatEndType,
            Integer durationWeeks,
            String requestedEndDate
    ) {
        return switch (repeatEndType) {
            case ONGOING -> null;

            case DURATION -> {
                validateWeekBasedDurationSupported(
                        repeatType
                );

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

    private void validateWeekBasedDurationSupported(
            MedicationRepeatType repeatType
    ) {
        if (repeatType == MedicationRepeatType.MONTHLY) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }
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

    private String formatMedicineTime(
            LocalTime medicineTime
    ) {
        int hour =
                medicineTime.getHour();

        int minute =
                medicineTime.getMinute();

        String period =
                hour < 12
                        ? "오전"
                        : "오후";

        int displayHour =
                hour % 12;

        if (displayHour == 0) {
            displayHour = 12;
        }

        if (minute == 0) {
            return period
                    + " "
                    + displayHour
                    + "시";
        }

        return String.format(
                "%s %d:%02d",
                period,
                displayHour,
                minute
        );
    }

    private User getReadableParentOrThrow(
            Long requesterUserId,
            Long parentUserId
    ) {
        User requester =
                medicationFamilyAuthorization
                        .getUserOrThrow(
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

        medicationFamilyAuthorization
                .validateChild(
                        requester
                );

        return medicationFamilyAuthorization
                .getSameFamilyParentOrThrow(
                        requester,
                        parentUserId
                );
    }

    private User getWritableParentOrThrow(
            Long requesterUserId,
            Long parentUserId
    ) {
        User requester =
                medicationFamilyAuthorization
                        .getUserOrThrow(
                                requesterUserId
                        );

        medicationFamilyAuthorization
                .validateChild(
                        requester
                );

        return medicationFamilyAuthorization
                .getSameFamilyParentOrThrow(
                        requester,
                        parentUserId
                );
    }
}