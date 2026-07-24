package com.example.senioron.domain.medication.service;

import com.example.senioron.domain.medication.dto.request.MedicationCreateRequest;
import com.example.senioron.domain.medication.dto.request.MedicationUpdateRequest;
import com.example.senioron.domain.medication.dto.response.MedicationCreateResponse;
import com.example.senioron.domain.medication.dto.response.MedicationReadResponse;
import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.medication.repository.MedicationLogRepository;
import com.example.senioron.domain.medication.repository.MedicationRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
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

    private static final List<String> DAY_ORDER = List.of(
            "SUN",
            "MON",
            "TUE",
            "WED",
            "THU",
            "FRI",
            "SAT"
    );

    private static final Map<String, String>
            DAY_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("SUN", "일"),
            Map.entry("MON", "월"),
            Map.entry("TUE", "화"),
            Map.entry("WED", "수"),
            Map.entry("THU", "목"),
            Map.entry("FRI", "금"),
            Map.entry("SAT", "토")
    );

    private final MedicationRepository medicationRepository;
    private final MedicationLogRepository medicationLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public MedicationCreateResponse createMedication(
            Long requesterUserId,
            Long parentUserId,
            MedicationCreateRequest request
    ) {
        User parentUser = getWritableParentOrThrow(
                requesterUserId,
                parentUserId
        );

        String medicationGroupId =
                UUID.randomUUID().toString();

        LocalDateTime effectiveFrom =
                LocalDateTime.now(KOREA_ZONE_ID);

        String medicineDays = normalizeAndJoinDays(
                request.getMedicineDays()
        );

        List<Medication> savedMedications =
                request.getMedicineTimes()
                        .stream()
                        .map(this::parseMedicineTime)
                        .map(medicineTime ->
                                medicationRepository.save(
                                        Medication.builder()
                                                .user(parentUser)
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
                                                .effectiveFrom(
                                                        effectiveFrom
                                                )
                                                .effectiveTo(null)
                                                .build()
                                )
                        )
                        .toList();

        List<Long> medicationIds =
                savedMedications.stream()
                        .map(Medication::getMedication_id)
                        .toList();

        List<String> medicineTimes =
                savedMedications.stream()
                        .map(Medication::getMedicineTime)
                        .map(LocalTime::toString)
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
                medicineTimes,
                request.getMedicineDays()
        );
    }

    public List<MedicationReadResponse> getMedications(
            Long requesterUserId,
            Long parentUserId
    ) {
        User parentUser = getReadableParentOrThrow(
                requesterUserId,
                parentUserId
        );

        return medicationRepository
                .findAllByUserAndEffectiveToIsNullOrderByMedicineTimeAsc(
                        parentUser
                )
                .stream()
                .map(medication ->
                        new MedicationReadResponse(
                                medication.getMedication_id(),
                                medication.getMedicationGroupId(),
                                medication.getMedicineName(),
                                medication.getIngredientName(),
                                formatMedicineTime(
                                        medication.getMedicineTime()
                                ),
                                formatMedicineDays(
                                        medication.getMedicineDays()
                                )
                        )
                )
                .toList();
    }

    @Transactional
    public void updateMedication(
            Long requesterUserId,
            Long parentUserId,
            MedicationUpdateRequest request
    ) {
        User parentUser = getWritableParentOrThrow(
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

        LocalDateTime changedAt =
                LocalDateTime.now(KOREA_ZONE_ID);

        existingMedications.forEach(
                medication -> medication.endMedication(changedAt)
        );

        medicationLogRepository.deleteFutureUntakenLogs(
                existingMedications,
                changedAt.toLocalDate(),
                changedAt.toLocalTime()
        );

        String medicineDays = normalizeAndJoinDays(
                request.getMedicineDays()
        );

        for (String timeString : request.getMedicineTimes()) {
            LocalTime medicineTime =
                    parseMedicineTime(timeString);

            Medication newMedication =
                    Medication.builder()
                            .user(parentUser)
                            .medicineName(
                                    request.getMedicineName()
                            )
                            .ingredientName(
                                    request.getIngredientName()
                            )
                            .medicineTime(medicineTime)
                            .medicineDays(medicineDays)
                            .medicationGroupId(
                                    request.getMedicationGroupId()
                            )
                            .effectiveFrom(changedAt)
                            .effectiveTo(null)
                            .build();

            medicationRepository.save(newMedication);
        }

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
        User parentUser = getWritableParentOrThrow(
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
                LocalDateTime.now(KOREA_ZONE_ID);

        medications.forEach(
                medication -> medication.endMedication(deletedAt)
        );

        medicationLogRepository.deleteFutureUntakenLogs(
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

    private User getReadableParentOrThrow(
            Long requesterUserId,
            Long parentUserId
    ) {
        User requester = getUserOrThrow(
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

        validateChild(requester);

        return getSameFamilyParentOrThrow(
                requester,
                parentUserId
        );
    }

    private User getWritableParentOrThrow(
            Long requesterUserId,
            Long parentUserId
    ) {
        User requester = getUserOrThrow(
                requesterUserId
        );

        validateChild(requester);

        return getSameFamilyParentOrThrow(
                requester,
                parentUserId
        );
    }

    private void validateChild(User requester) {
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
        User parentUser = getUserOrThrow(
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

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );
    }

    private String normalizeAndJoinDays(
            List<String> medicineDays
    ) {
        Set<String> normalizedDays =
                medicineDays.stream()
                        .map(String::trim)
                        .map(value ->
                                value.toUpperCase(
                                        Locale.ROOT
                                )
                        )
                        .map(this::normalizeDay)
                        .filter(Objects::nonNull)
                        .collect(
                                Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        );

        if (normalizedDays.size()
                != new LinkedHashSet<>(
                medicineDays
        ).size()) {
            boolean containsInvalidDay =
                    medicineDays.stream()
                            .map(String::trim)
                            .map(value ->
                                    value.toUpperCase(
                                            Locale.ROOT
                                    )
                            )
                            .map(this::normalizeDay)
                            .anyMatch(Objects::isNull);

            if (containsInvalidDay) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST
                );
            }
        }

        return DAY_ORDER.stream()
                .filter(normalizedDays::contains)
                .collect(Collectors.joining(","));
    }

    private LocalTime parseMedicineTime(
            String time
    ) {
        try {
            return LocalTime.parse(time);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }
    }

    private String formatMedicineTime(
            LocalTime medicineTime
    ) {
        int hour = medicineTime.getHour();
        int minute = medicineTime.getMinute();

        String period =
                hour < 12 ? "오전" : "오후";

        int displayHour = hour % 12;

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

    private String formatMedicineDays(
            String medicineDays
    ) {
        if (medicineDays == null
                || medicineDays.isBlank()) {
            return "";
        }

        Set<String> normalizedDays =
                Arrays.stream(
                                medicineDays.split(",")
                        )
                        .map(String::trim)
                        .map(value ->
                                value.toUpperCase(
                                        Locale.ROOT
                                )
                        )
                        .map(this::normalizeDay)
                        .filter(Objects::nonNull)
                        .collect(
                                Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        );

        if (normalizedDays.size() == 7) {
            return "매일";
        }

        return DAY_ORDER.stream()
                .filter(normalizedDays::contains)
                .map(DAY_DISPLAY_NAMES::get)
                .collect(Collectors.joining(", "));
    }

    private String normalizeDay(String day) {
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