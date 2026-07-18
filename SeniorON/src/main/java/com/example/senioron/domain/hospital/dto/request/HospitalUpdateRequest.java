package com.example.senioron.domain.hospital.dto.request;


import com.example.senioron.domain.hospital.entity.HospitalReminderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@NoArgsConstructor
@Schema(description = "병원 일정 수정 요청")
public class HospitalUpdateRequest {

    @NotBlank(message = "병원 이름은 필수입니다.")
    @Schema(description = "변경할 병원 이름", example = "서울대학교병원" )
    private String hospitalName;

    @NotBlank(message = "진료 과목은 필수입니다.")
    @Schema(description = "변경할 진료 과목", example = "내과")
    private String department;

    @NotBlank(message = "진료 날짜는 필수입니다.")
    @Pattern(
            regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
            message = "진료 날짜는 올바른 형식(YYYY-MM-DD)이어야 합니다. (예: 2026-07-14)"
    )
    @Schema(description = "변경할 진료 날짜 (YYYY-MM-DD)", example = "2026-07-14")
    private String scheduleDate;

    @NotBlank(message = "진료 시간은 필수입니다.")
    @Pattern(
            regexp = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$",
            message = "진료 시간은 올바른 24시간 형식(HH:mm)이어야 합니다. (예: 10:30)"
    )
    @Schema(description = "변경할 진료 시간 (HH:mm)", example = "10:30")
    private String scheduleTime;

    @NotNull(message = "알림 설정은 필수입니다.")
    @Schema(description = "변경할 알림 설정 타입", example = "DAY_BEFORE")
    private HospitalReminderType reminderType;


}
