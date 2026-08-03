package com.example.senioron.domain.hospital.repository;

import com.example.senioron.domain.hospital.entity.Hospital;
import com.example.senioron.domain.hospital.entity.HospitalReminderType;
import com.example.senioron.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HospitalRepository
        extends JpaRepository<Hospital, Long> {

    List<Hospital>
    findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
            User user,
            LocalDate start,
            LocalDate end
    );

    List<Hospital>
    findByUserAndScheduleDateOrderByScheduleTimeAsc(
            User user,
            LocalDate scheduleDate
    );

    @Query("""
            SELECT DISTINCT hospital.scheduleDate
            FROM Hospital hospital
            WHERE hospital.user = :user
              AND (
                    hospital.scheduleDate > :today
                    OR (
                        hospital.scheduleDate = :today
                        AND hospital.scheduleTime >= :currentTime
                    )
              )
            ORDER BY hospital.scheduleDate ASC
            """)
    List<LocalDate> findUpcomingScheduleDates(
            @Param("user")
            User user,

            @Param("today")
            LocalDate today,

            @Param("currentTime")
            LocalTime currentTime,

            Pageable pageable
    );

    @Query("""
            SELECT hospital
            FROM Hospital hospital
            WHERE hospital.user = :user
              AND hospital.scheduleDate IN :scheduleDates
              AND (
                    hospital.scheduleDate > :today
                    OR (
                        hospital.scheduleDate = :today
                        AND hospital.scheduleTime >= :currentTime
                    )
              )
            ORDER BY hospital.scheduleDate ASC,
                     hospital.scheduleTime ASC
            """)
    List<Hospital> findUpcomingHospitalsByDates(
            @Param("user")
            User user,

            @Param("scheduleDates")
            List<LocalDate> scheduleDates,

            @Param("today")
            LocalDate today,

            @Param("currentTime")
            LocalTime currentTime
    );

    @Query("""
            SELECT DISTINCT hospital
            FROM Hospital hospital
            JOIN FETCH hospital.user parentUser
            LEFT JOIN FETCH parentUser.family
            WHERE hospital.scheduleTime = :scheduleTime
              AND (
                    (
                        hospital.reminderType = :sameDayType
                        AND hospital.scheduleDate = :sameDayScheduleDate
                    )
                    OR
                    (
                        hospital.reminderType = :dayBeforeType
                        AND hospital.scheduleDate = :dayBeforeScheduleDate
                    )
              )
            ORDER BY hospital.scheduleDate ASC,
                     hospital.scheduleTime ASC
            """)
    List<Hospital> findHospitalReminderTargets(
            @Param("sameDayScheduleDate")
            LocalDate sameDayScheduleDate,

            @Param("dayBeforeScheduleDate")
            LocalDate dayBeforeScheduleDate,

            @Param("scheduleTime")
            LocalTime scheduleTime,

            @Param("sameDayType")
            HospitalReminderType sameDayType,

            @Param("dayBeforeType")
            HospitalReminderType dayBeforeType
    );
}