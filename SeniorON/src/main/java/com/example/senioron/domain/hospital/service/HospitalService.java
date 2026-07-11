package com.example.senioron.domain.hospital.service;

import com.example.senioron.domain.hospital.dto.request.HospitalCreateRequest;
import com.example.senioron.domain.hospital.dto.response.HospitalCreateResponse;
import com.example.senioron.domain.hospital.entity.Hospital;
import com.example.senioron.domain.hospital.repository.HospitalRepository;
import com.example.senioron.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HospitalService {

    private final HospitalRepository hospitalRepository;

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
}