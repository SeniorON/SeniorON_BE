package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.AdminReportType;
import com.example.senioron.domain.family.entity.FamilyPhotoReport;
import com.example.senioron.domain.family.entity.PhotoReportReason;
import com.example.senioron.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 게시물 신고와 게시자 신고는 테이블이 달라 합쳐서 조회해야 하므로 네이티브 쿼리를 사용한다.
 * type과 keyword는 조건이 없을 때 빈 문자열로 전달한다.
 */
public interface AdminReportQueryRepository extends Repository<FamilyPhotoReport, Long> {

    // 번호와 누적 횟수는 필터 전에 계산해야 탭이나 검색에 따라 달라지지 않는다.
    @Query(
            value = """
                    SELECT numbered.rowNo AS rowNo,
                           numbered.reportType AS reportType,
                           numbered.reportId AS reportId,
                           COALESCE(target_user.name, '탈퇴회원') AS targetName,
                           photo.created_at AS photoUploadedAt,
                           numbered.reason AS reason,
                           reporter_user.name AS reporterName,
                           reporter_user.status AS reporterStatus,
                           numbered.createdAt AS createdAt,
                           numbered.cumulativeCount AS cumulativeCount
                    FROM (
                        SELECT merged.*,
                               ROW_NUMBER() OVER (
                                   ORDER BY merged.createdAt, merged.reportType, merged.reportId
                               ) AS rowNo
                        FROM (
                            SELECT 'PHOTO' AS reportType,
                                   fpr.family_photo_report_id AS reportId,
                                   fpr.reporter_user_id AS reporterUserId,
                                   fpr.reported_user_id AS targetUserId,
                                   fpr.family_photo_id AS familyPhotoId,
                                   fpr.reason AS reason,
                                   fpr.created_at AS createdAt,
                                   COUNT(*) OVER (PARTITION BY fpr.image_key) AS cumulativeCount
                            FROM family_photo_report fpr
                            UNION ALL
                            SELECT 'USER',
                                   fur.family_user_report_id,
                                   fur.reporter_user_id,
                                   fur.reported_user_id,
                                   NULL,
                                   fur.reason,
                                   fur.created_at,
                                   COUNT(*) OVER (PARTITION BY fur.reported_user_id)
                            FROM family_user_report fur
                        ) merged
                    ) numbered
                    JOIN users reporter_user ON reporter_user.users_id = numbered.reporterUserId
                    LEFT JOIN users target_user ON target_user.users_id = numbered.targetUserId
                    LEFT JOIN family_photo photo ON photo.family_photo_id = numbered.familyPhotoId
                    WHERE (:type = '' OR numbered.reportType = :type)
                      AND (
                          :keyword = ''
                          OR LOWER(COALESCE(target_user.name, '탈퇴회원')) LIKE :keyword ESCAPE '!'
                          OR LOWER(reporter_user.name) LIKE :keyword ESCAPE '!'
                      )
                    ORDER BY numbered.rowNo DESC
                    LIMIT :pageSize OFFSET :pageOffset
                    """,
            nativeQuery = true
    )
    List<ReportRow> findReportPage(
            @Param("type") String type,
            @Param("keyword") String keyword,
            @Param("pageSize") int pageSize,
            @Param("pageOffset") long pageOffset
    );

    // 탭 건수는 검색어만 반영한다.
    @Query(
            value = """
                    SELECT merged.reportType AS reportType,
                           COUNT(*) AS reportCount
                    FROM (
                        SELECT 'PHOTO' AS reportType,
                               fpr.reporter_user_id AS reporterUserId,
                               fpr.reported_user_id AS targetUserId
                        FROM family_photo_report fpr
                        UNION ALL
                        SELECT 'USER',
                               fur.reporter_user_id,
                               fur.reported_user_id
                        FROM family_user_report fur
                    ) merged
                    JOIN users reporter_user ON reporter_user.users_id = merged.reporterUserId
                    LEFT JOIN users target_user ON target_user.users_id = merged.targetUserId
                    WHERE :keyword = ''
                       OR LOWER(COALESCE(target_user.name, '탈퇴회원')) LIKE :keyword ESCAPE '!'
                       OR LOWER(reporter_user.name) LIKE :keyword ESCAPE '!'
                    GROUP BY merged.reportType
                    """,
            nativeQuery = true
    )
    List<ReportTypeCount> countByType(@Param("keyword") String keyword);

    @Query(
            value = """
                    SELECT COUNT(*) AS totalCount,
                           COUNT(CASE WHEN merged.created_at >= :todayStart
                                       AND merged.created_at < :now THEN 1 END) AS todayCount,
                           COUNT(CASE WHEN merged.created_at >= :yesterdayStart
                                       AND merged.created_at < :yesterdayEnd THEN 1 END) AS yesterdayCount,
                           COUNT(CASE WHEN merged.created_at >= :weekStart
                                       AND merged.created_at < :now THEN 1 END) AS weekCount,
                           COUNT(CASE WHEN merged.created_at >= :lastWeekStart
                                       AND merged.created_at < :lastWeekEnd THEN 1 END) AS lastWeekCount
                    FROM (
                        SELECT fpr.created_at FROM family_photo_report fpr
                        UNION ALL
                        SELECT fur.created_at FROM family_user_report fur
                    ) merged
                    """,
            nativeQuery = true
    )
    ReportPeriodCounts countByPeriod(
            @Param("now") LocalDateTime now,
            @Param("todayStart") LocalDateTime todayStart,
            @Param("yesterdayStart") LocalDateTime yesterdayStart,
            @Param("yesterdayEnd") LocalDateTime yesterdayEnd,
            @Param("weekStart") LocalDateTime weekStart,
            @Param("lastWeekStart") LocalDateTime lastWeekStart,
            @Param("lastWeekEnd") LocalDateTime lastWeekEnd
    );

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT fur.reported_user_id
                        FROM family_user_report fur
                        GROUP BY fur.reported_user_id
                        HAVING COUNT(*) >= :threshold
                    ) repeated_user
                    """,
            nativeQuery = true
    )
    long countRepeatedReportedUsers(@Param("threshold") long threshold);

    interface ReportRow {

        long getRowNo();

        AdminReportType getReportType();

        Long getReportId();

        String getTargetName();

        LocalDateTime getPhotoUploadedAt();

        PhotoReportReason getReason();

        String getReporterName();

        UserStatus getReporterStatus();

        LocalDateTime getCreatedAt();

        long getCumulativeCount();
    }

    interface ReportTypeCount {

        AdminReportType getReportType();

        long getReportCount();
    }

    interface ReportPeriodCounts {

        long getTotalCount();

        long getTodayCount();

        long getYesterdayCount();

        long getWeekCount();

        long getLastWeekCount();
    }
}
