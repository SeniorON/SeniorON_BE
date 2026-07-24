package com.example.senioron.domain.medication.service;


import java.util.Locale;

import com.example.senioron.domain.medication.dto.request.MedicationCreateRequest;
import com.example.senioron.domain.medication.dto.request.MedicationUpdateRequest;
import com.example.senioron.domain.medication.dto.response.MedicationCreateResponse;
import com.example.senioron.domain.medication.dto.response.MedicationReadResponse;
import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.medication.entity.MedicationLog;
import com.example.senioron.domain.medication.repository.MedicationLogRepository;
import com.example.senioron.domain.medication.repository.MedicationRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationService {

    private static final int LOG_SCHEDULE_DAYS = 30;

    private static final List<String> DAY_ORDER = List.of(
            "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"
    );

    private static final Map<String, String> DAY_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("SUN", "일"),
            Map.entry("MON", "월"),
            Map.entry("TUE", "화"),
            Map.entry("WED", "수"),
            Map.entry("THU", "목"),
            Map.entry("FRI", "금"),
            Map.entry("SAT", "토")
    );

    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;
    private final MedicationLogRepository medicationLogRepository;

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

        String medicineDays = String.join(
                ",",
                request.getMedicineDays()
        );

        String medicationGroupId =
                java.util.UUID.randomUUID().toString();

        List<Long> medicationIds = new ArrayList<>();
        List<String> medicineTimes = new ArrayList<>();
        List<MedicationLog> logsToSave = new ArrayList<>();

        LocalDate startDate = LocalDate.now(
                ZoneId.of("Asia/Seoul")
        );

        for (String timeStr : request.getMedicineTimes()) {
            LocalTime medicineTime = parseMedicineTime(timeStr);

            Medication medication = Medication.builder()
                    .user(parentUser)
                    .medicineName(request.getMedicineName())
                    .ingredientName(request.getIngredientName())
                    .medicineTime(medicineTime)
                    .medicineDays(medicineDays)
                    .medicationGroupId(medicationGroupId)
                    .build();

            Medication savedMedication =
                    medicationRepository.save(medication);

            medicationIds.add(savedMedication.getMedication_id());
            medicineTimes.add(
                    savedMedication.getMedicineTime().toString()
            );

            for (int i = 0; i < LOG_SCHEDULE_DAYS; i++) {
                LocalDate currentDate = startDate.plusDays(i);

                String normalizedCurrentDay = normalizeDay(
                        currentDate.getDayOfWeek().name()
                );

                boolean isDayIncluded =
                        request.getMedicineDays().stream()
                                .map(String::trim)
                                .map(value -> value.toUpperCase(Locale.ROOT))
                                .map(this::normalizeDay)
                                .anyMatch(day ->
                                        Objects.equals(
                                                day,
                                                normalizedCurrentDay
                                        )
                                );

                if (isDayIncluded) {
                    logsToSave.add(
                            MedicationLog.builder()
                                    .user(parentUser)
                                    .medication(savedMedication)
                                    .plannedDate(currentDate)
                                    .plannedTime(medicineTime)
                                    .isTaken(false)
                                    .build()
                    );
                }
            }
        }

        if (!logsToSave.isEmpty()) {
            medicationLogRepository.saveAll(logsToSave);
        }

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

    private String convertDayOfWeekToKorean(
            java.time.DayOfWeek dayOfWeek
    ) {
        return switch (dayOfWeek) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    public List<MedicationReadResponse> getMedications(Long userId) {
        User user = getUserOrThrow(userId);

        return medicationRepository
                .findAllByUserOrderByMedicineTimeAsc(user)
                .stream()
                .map(medication -> new MedicationReadResponse(
                        medication.getMedication_id(),
                        medication.getMedicineName(),
                        medication.getIngredientName(),
                        formatMedicineTime(
                                medication.getMedicineTime()
                        ),
                        formatMedicineDays(
                                medication.getMedicineDays()
                        )
                ))
                .collect(Collectors.toList());
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );
    }

    private User getWritableParentOrThrow(
            Long requesterUserId,
            Long parentUserId
    ) {
        User requester = getUserOrThrow(requesterUserId);

        if (requester.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (requester.getManagerType() != ManagerType.PRIMARY) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (requester.getFamily() == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_FOUND
            );
        }

        User parentUser = getUserOrThrow(parentUserId);

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

    private LocalTime parseMedicineTime(String time) {
        try {
            return LocalTime.parse(time);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private String formatMedicineTime(LocalTime medicineTime) {
        int hour = medicineTime.getHour();
        int minute = medicineTime.getMinute();

        String period = hour < 12 ? "오전" : "오후";

        int displayHour = hour % 12;

        if (displayHour == 0) {
            displayHour = 12;
        }

        if (minute == 0) {
            return period + " " + displayHour + "시";
        }

        return String.format(
                "%s %d:%02d",
                period,
                displayHour,
                minute
        );
    }

    private String formatMedicineDays(String medicineDays) {
        if (medicineDays == null || medicineDays.isBlank()) {
            return "";
        }

        Set<String> normalizedDays =
                Arrays.stream(medicineDays.split(","))
                        .map(String::trim)
                        .map(value -> value.toUpperCase(Locale.ROOT))
                        .map(day -> {
                            String normalizedDay =
                                    normalizeDay(day);

                            if (normalizedDay == null) {
                                log.warn(
                                        "Unrecognized medicine day token: {}",
                                        day
                                );
                            }

                            return normalizedDay;
                        })
                        .filter(day -> day != null)
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

    @Transactional
    public void deleteMedicationGroup(
            Long userId,
            String medicationGroupId
    ) {
        User user = getUserOrThrow(userId);

        List<Medication> medications =
                medicationRepository
                        .findByUserAndMedicationGroupId(
                                user,
                                medicationGroupId
                        );

        if (!medications.isEmpty()) {
            medicationLogRepository.deleteByMedicationIn(
                    medications
            );

            medicationRepository
                    .deleteByUserAndMedicationGroupId(
                            user,
                            medicationGroupId
                    );
        }

        log.info(
                "성공적으로 약 그룹과 복약 로그를 삭제했습니다. UserId: {}, GroupId: {}",
                userId,
                medicationGroupId
        );
    }

    @Transactional
    public void updateMedication(
            Long userId,
            MedicationUpdateRequest request
    ) {
        User user = getUserOrThrow(userId);

        boolean exists =
                medicationRepository
                        .existsByUserAndMedicationGroupId(
                                user,
                                request.getMedicationGroupId()
                        );

        if (!exists) {
            throw new BusinessException(
                    ErrorCode.MEDICATION_NOT_FOUND
            );
        }

        List<Medication> medications =
                medicationRepository
                        .findByUserAndMedicationGroupId(
                                user,
                                request.getMedicationGroupId()
                        );

        if (!medications.isEmpty()) {
            medicationLogRepository.deleteByMedicationIn(
                    medications
            );
        }

        medicationRepository.deleteByUserAndMedicationGroupId(
                user,
                request.getMedicationGroupId()
        );

        String medicineDays = String.join(
                ",",
                request.getMedicineDays()
        );

        for (String timeStr : request.getMedicineTimes()) {
            LocalTime medicineTime;

            try {
                medicineTime = LocalTime.parse(timeStr);
            } catch (DateTimeParseException exception) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST
                );
            }

            Medication medication = Medication.builder()
                    .user(user)
                    .medicineName(request.getMedicineName())
                    .ingredientName(request.getIngredientName())
                    .medicineTime(medicineTime)
                    .medicineDays(medicineDays)
                    .medicationGroupId(
                            request.getMedicationGroupId()
                    )
                    .build();

            medicationRepository.save(medication);
        }

        log.info(
                "성공적으로 약 그룹을 수정했습니다. UserId: {}, GroupId: {}",
                userId,
                request.getMedicationGroupId()
        );
    }
}