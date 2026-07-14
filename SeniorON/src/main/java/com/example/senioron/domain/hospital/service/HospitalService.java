package com.example.senioron.domain.hospital.service;


import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import com.example.senioron.domain.hospital.dto.response.HospitalListResponse;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.domain.hospital.dto.request.HospitalCreateRequest;
import com.example.senioron.domain.hospital.dto.response.HospitalCreateResponse;
import com.example.senioron.domain.hospital.entity.Hospital;
import com.example.senioron.domain.hospital.repository.HospitalRepository;
import com.example.senioron.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HospitalService {

    private final HospitalRepository hospitalRepository;

    // 병원 일정 등록 부분
    @Transactional
    public HospitalCreateResponse createHospital(
            User user,
            HospitalCreateRequest request
    ) {
        Hospital hospital = Hospital.builder()
                .user(user)
                .hospitalName(request.getHospitalName())
                .department(request.getDepartment())
                .scheduleDate(request.getScheduleDate())
                .scheduleTime(request.getScheduleTime())
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
    //일정 목록 조회 부분
    public List<HospitalListResponse> getHospitalByMonth(User user, int year, int month) {

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<Hospital> hospitals = hospitalRepository.findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(user, startDate, endDate);

        return hospitals.stream()
                .map(hospital -> HospitalListResponse.builder()
                        .hospitalId(hospital.getHospital_id())
                        .hospitalName(hospital.getHospitalName())
                        .department(hospital.getDepartment())
                        .scheduleDate(hospital.getScheduleDate())
                        .scheduleTime(hospital.getScheduleTime())
                        .reminderType(hospital.getReminderType() != null ? hospital.getReminderType().name() : null)
                        .build())
                .collect(Collectors.toList());



    }

    //병원 일정 삭제 부분
    @Transactional
    public void deleteHospital(User user, Long hospitalId) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.HOSPITAL_SCHEDULE_NOT_FOUND)
                );

        if (!hospital.getUser().getUsersId().equals(user.getUsersId())) {
            throw new BusinessException(ErrorCode.HOSPITAL_SCHEDULE_NOT_FOUND);
        }

        hospitalRepository.delete(hospital);
    }

}