package com.example.senioron.domain.medication.support;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class MedicationWeekdayUtils {

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

    private MedicationWeekdayUtils() {
    }

    public static String normalizeAndJoinDays(
            List<String> medicineDays
    ) {
        if (medicineDays == null
                || medicineDays.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

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

        if (containsInvalidDay) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        Set<String> normalizedDays =
                medicineDays.stream()
                        .map(
                                String::trim
                        )
                        .map(value ->
                                value.toUpperCase(
                                        Locale.ROOT
                                )
                        )
                        .map(
                                MedicationWeekdayUtils::normalizeDay
                        )
                        .collect(
                                Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        );

        if (normalizedDays.isEmpty()) {
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

    public static List<String> toDisplayDayList(
            String medicineDays
    ) {
        Set<String> normalizedDays =
                parseNormalizedDays(
                        medicineDays
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

    public static String formatMedicineDays(
            String medicineDays
    ) {
        Set<String> normalizedDays =
                parseNormalizedDays(
                        medicineDays
                );

        if (normalizedDays.isEmpty()) {
            return "";
        }

        if (normalizedDays.size() == 7) {
            return "매일";
        }

        return DAY_ORDER.stream()
                .filter(
                        normalizedDays::contains
                )
                .map(
                        DAY_DISPLAY_NAMES::get
                )
                .collect(
                        Collectors.joining(", ")
                );
    }

    public static boolean isSelectedWeekday(
            String medicineDays,
            LocalDate date
    ) {
        if (date == null) {
            return false;
        }

        String targetDay =
                normalizeDay(
                        date.getDayOfWeek()
                                .name()
                );

        return parseNormalizedDays(
                medicineDays
        )
                .stream()
                .anyMatch(day ->
                        Objects.equals(
                                day,
                                targetDay
                        )
                );
    }

    public static String normalizeDay(
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

    private static Set<String> parseNormalizedDays(
            String medicineDays
    ) {
        if (medicineDays == null
                || medicineDays.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(
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
                        MedicationWeekdayUtils::normalizeDay
                )
                .filter(
                        Objects::nonNull
                )
                .collect(
                        Collectors.toCollection(
                                LinkedHashSet::new
                        )
                );
    }
}