package com.example.senioron.domain.hospital.repository;

import com.example.senioron.domain.user.entity.User;
import java.time.LocalDate;
import java.util.List;

import com.example.senioron.domain.hospital.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    List<Hospital> findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
            User user, LocalDate start, LocalDate end
    );

    List<Hospital> findByUserAndScheduleDateOrderByScheduleTimeAsc(User user, LocalDate scheduleDate);
}