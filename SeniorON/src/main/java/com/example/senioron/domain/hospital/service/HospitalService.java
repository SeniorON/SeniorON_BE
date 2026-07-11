package com.example.senioron.domain.hospital.service;

import com.example.senioron.domain.hospital.dto.request.HospitalCreateRequest;
import com.example.senioron.domain.hospital.dto.response.HospitalCreateResponse;
import com.example.senioron.domain.hospital.entity.Hospital;
import com.example.senioron.domain.hospital.repository.HospitalRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
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
            Long userId,
            HospitalCreateRequest request
    ) {
        User user = getUserOrThrow(userId);

        Hospital hospital = Hospital.builder()
                .user(user)
                .hospitalName(request.getHospitalName())
                .department(request.getDepartment())
                .scheduleDate(request.getScheduleDate())
                .scheduleTime(request.getScheduleTime())
                .reminderType(request.getReminderType())
                .build();

        Hospital savedHospital = hospitalRepository.save(hospital);

        return new HospitalCreateResponse(
                savedHospital.getHospital_id(),
                savedHospital.getHospitalName(),
                savedHospital.getDepartment(),
                savedHospital.getScheduleDate(),
                savedHospital.getScheduleTime(),
                savedHospital.getReminderType()
        );
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );
    }
}