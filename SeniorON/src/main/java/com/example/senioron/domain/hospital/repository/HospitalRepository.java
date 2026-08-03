package com.example.senioron.domain.hospital.repository;

import com.example.senioron.domain.hospital.entity.Hospital;
import com.example.senioron.domain.hospital.entity.HospitalReminderType;
import com.example.senioron.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HospitalRepository
        extends JpaRepository<Hospital, Long> {

    List<Hospital>
    findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
            User user,
            LocalDate startDate,
            LocalDate endDate
    );

    List<Hospital>
    findByUserAndScheduleDateOrderByScheduleTimeAsc(
            User user,
            LocalDate scheduleDate
    );

    @Query("""
            SELECT DISTINCT h.scheduleDate
            FROM Hospital h
            WHERE h.user = :user
              AND (
                    h.scheduleDate > :today
                    OR (
                        h.scheduleDate = :today
                        AND h.scheduleTime >= :currentTime
                    )
                  )
            ORDER BY h.scheduleDate ASC
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
            SELECT h
            FROM Hospital h
            WHERE h.user = :user
              AND h.scheduleDate IN :scheduleDates
              AND (
                    h.scheduleDate > :today
                    OR (
                        h.scheduleDate = :today
                        AND h.scheduleTime >= :currentTime
                    )
                  )
            ORDER BY h.scheduleDate ASC,
                     h.scheduleTime ASC,
                     h.hospital_id ASC
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

    @EntityGraph(
            attributePaths = {
                    "user",
                    "user.family"
            }
    )
    @Query("""
            SELECT h
            FROM Hospital h
            WHERE h.reminderSentAt IS NULL
              AND h.reminderType <> :noneType
              AND h.scheduleDate BETWEEN :startScheduleDate
                                     AND :endScheduleDate
            ORDER BY h.scheduleDate ASC,
                     h.scheduleTime ASC,
                     h.hospital_id ASC
            """)
    List<Hospital> findPendingReminderCandidates(
            @Param("noneType")
            HospitalReminderType noneType,

            @Param("startScheduleDate")
            LocalDate startScheduleDate,

            @Param("endScheduleDate")
            LocalDate endScheduleDate
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            UPDATE Hospital h
            SET h.reminderSentAt = :sentAt
            WHERE h.hospital_id = :hospitalId
              AND h.reminderSentAt IS NULL
              AND h.scheduleDate = :scheduleDate
              AND h.scheduleTime = :scheduleTime
              AND h.reminderType = :reminderType
            """)
    int markReminderSentAt(
            @Param("hospitalId")
            Long hospitalId,

            @Param("scheduleDate")
            LocalDate scheduleDate,

            @Param("scheduleTime")
            LocalTime scheduleTime,

            @Param("reminderType")
            HospitalReminderType reminderType,

            @Param("sentAt")
            LocalDateTime sentAt
    );
}