package com.example.senioron.domain.hospital.service;

import com.example.senioron.domain.hospital.dto.request.HospitalCreateRequest;
import com.example.senioron.domain.hospital.dto.request.HospitalUpdateRequest;
import com.example.senioron.domain.hospital.dto.response.HospitalCreateResponse;
import com.example.senioron.domain.hospital.dto.response.HospitalDetailResponse;
import com.example.senioron.domain.hospital.dto.response.HospitalListResponse;
import com.example.senioron.domain.hospital.entity.Hospital;
import com.example.senioron.domain.hospital.repository.HospitalRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HospitalService {

    private final HospitalRepository hospitalRepository;
    private final UserRepository userRepository;

    @Transactional
    public HospitalCreateResponse createHospital(
            Long requesterUserId,
            Long parentUserId,
            HospitalCreateRequest request
    ) {
        User parentUser = getWritableParentOrThrow(
                requesterUserId,
                parentUserId
        );

        LocalDate date;
        LocalTime time;

        try {
            date = LocalDate.parse(request.getScheduleDate());
            time = LocalTime.parse(request.getScheduleTime());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        Hospital hospital = Hospital.builder()
                .user(parentUser)
                .hospitalName(request.getHospitalName())
                .scheduleDate(date)
                .scheduleTime(time)
                .department(request.getDepartment())
                .reminderType(request.getReminderType())
                .build();

        Hospital savedHospital = hospitalRepository.save(hospital);

        return HospitalCreateResponse.builder()
                .hospitalId(savedHospital.getHospital_id())
                .hospitalName(savedHospital.getHospitalName())
                .department(savedHospital.getDepartment())
                .scheduleDate(savedHospital.getScheduleDate())
                .scheduleTime(savedHospital.getScheduleTime())
                .reminderType(savedHospital.getReminderType())
                .build();
    }

    public List<HospitalListResponse> getHospitalByMonth(
            Long requesterUserId,
            Long parentUserId,
            int year,
            int month
    ) {
        User parentUser = getReadableParentOrThrow(
                requesterUserId,
                parentUserId
        );

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate =
                startDate.withDayOfMonth(
                        startDate.lengthOfMonth()
                );

        List<Hospital> hospitals =
                hospitalRepository
                        .findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                                parentUser,
                                startDate,
                                endDate
                        );

        return hospitals.stream()
                .map(hospital ->
                        HospitalListResponse.builder()
                                .hospitalId(
                                        hospital.getHospital_id()
                                )
                                .hospitalName(
                                        hospital.getHospitalName()
                                )
                                .department(
                                        hospital.getDepartment()
                                )
                                .scheduleDate(
                                        hospital.getScheduleDate()
                                )
                                .scheduleTime(
                                        hospital.getScheduleTime()
                                )
                                .reminderType(
                                        hospital.getReminderType() != null
                                                ? hospital.getReminderType().name()
                                                : null
                                )
                                .build()
                )
                .toList();
    }

    @Transactional
    public void deleteHospital(
            Long requesterUserId,
            Long parentUserId,
            Long hospitalId
    ) {
        User parentUser = getWritableParentOrThrow(
                requesterUserId,
                parentUserId
        );

        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.HOSPITAL_SCHEDULE_NOT_FOUND
                        )
                );

        validateHospitalOwner(hospital, parentUser);

        hospitalRepository.delete(hospital);
    }

    @Transactional
    public void updateHospital(
            Long requesterUserId,
            Long parentUserId,
            Long hospitalId,
            HospitalUpdateRequest request
    ) {
        User parentUser = getWritableParentOrThrow(
                requesterUserId,
                parentUserId
        );

        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.HOSPITAL_SCHEDULE_NOT_FOUND
                        )
                );

        validateHospitalOwner(hospital, parentUser);

        LocalDate date;
        LocalTime time;

        try {
            date = LocalDate.parse(request.getScheduleDate());
            time = LocalTime.parse(request.getScheduleTime());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        hospital.updateHospital(
                request.getHospitalName(),
                request.getDepartment(),
                date,
                time,
                request.getReminderType()
        );
    }

    public List<HospitalDetailResponse> getHospitalByDate(
            Long requesterUserId,
            Long parentUserId,
            LocalDate date
    ) {
        User parentUser = getReadableParentOrThrow(
                requesterUserId,
                parentUserId
        );

        List<Hospital> hospitals =
                hospitalRepository
                        .findByUserAndScheduleDateOrderByScheduleTimeAsc(
                                parentUser,
                                date
                        );

        return hospitals.stream()
                .map(hospital ->
                        HospitalDetailResponse.builder()
                                .hospitalId(
                                        hospital.getHospital_id()
                                )
                                .hospitalName(
                                        hospital.getHospitalName()
                                )
                                .department(
                                        hospital.getDepartment()
                                )
                                .scheduleDate(
                                        hospital.getScheduleDate()
                                )
                                .scheduleTime(
                                        hospital.getScheduleTime()
                                )
                                .reminderType(
                                        hospital.getReminderType() != null
                                                ? hospital.getReminderType().name()
                                                : null
                                )
                                .build()
                )
                .toList();
    }

    private User getReadableParentOrThrow(
            Long requesterUserId,
            Long parentUserId
    ) {
        User requester = getUserOrThrow(requesterUserId);

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
        User requester = getUserOrThrow(requesterUserId);

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

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );
    }

    private void validateHospitalOwner(
            Hospital hospital,
            User parentUser
    ) {
        if (!Objects.equals(
                hospital.getUser().getUsersId(),
                parentUser.getUsersId()
        )) {
            throw new BusinessException(
                    ErrorCode.HOSPITAL_SCHEDULE_NOT_FOUND
            );
        }
    }
}