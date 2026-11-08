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
    // 누적 횟수: 게시물 신고는 같은 사진 기준, 게시자 신고는 게시자가 받은 전체 신고(게시물 + 게시자) 기준
    @Query(
            value = """
                    SELECT numbered.rowNo AS rowNo,
                           numbered.reportType AS reportType,
                           numbered.reportId AS reportId,
                           COALESCE(target_user.name, '탈퇴회원') AS targetName,
                           COALESCE(numbered.photoUploadedAt, photo.created_at) AS photoUploadedAt,
                           numbered.reason AS reason,
                           reporter_user.name AS reporterName,
                           reporter_user.status AS reporterStatus,
                           numbered.createdAt AS createdAt,
                           numbered.cumulativeCount AS cumulativeCount
                    FROM (
                        SELECT merged.reportType,
                               merged.reportId,
                               merged.reporterUserId,
                               merged.targetUserId,
                               merged.familyPhotoId,
                               merged.photoUploadedAt,
                               merged.reason,
                               merged.createdAt,
                               ROW_NUMBER() OVER (
                                   ORDER BY merged.createdAt, merged.reportType, merged.reportId
                               ) AS rowNo,
                               CASE WHEN merged.reportType = 'PHOTO'
                                    THEN COUNT(*) OVER (PARTITION BY merged.imageKey)
                                    ELSE COUNT(*) OVER (PARTITION BY merged.targetUserId)
                               END AS cumulativeCount
                        FROM (
                            SELECT 'PHOTO' AS reportType,
                                   fpr.family_photo_report_id AS reportId,
                                   fpr.reporter_user_id AS reporterUserId,
                                   fpr.reported_user_id AS targetUserId,
                                   fpr.family_photo_id AS familyPhotoId,
                                   fpr.image_key AS imageKey,
                                   fpr.photo_uploaded_at AS photoUploadedAt,
                                   fpr.reason AS reason,
                                   fpr.created_at AS createdAt
                            FROM family_photo_report fpr
                            UNION ALL
                            SELECT 'USER',
                                   fur.family_user_report_id,
                                   fur.reporter_user_id,
                                   fur.reported_user_id,
                                   NULL,
                                   NULL,
                                   NULL,
                                   fur.reason,
                                   fur.created_at
                            FROM family_user_report fur
                        ) merged
                    ) numbered
                    JOIN users reporter_user ON reporter_user.users_id = numbered.reporterUserId
                    LEFT JOIN users target_user ON target_user.users_id = numbered.targetUserId
                    LEFT JOIN family_photo photo ON photo.family_photo_id = numbered.familyPhotoId
                    WHERE (:type = '' OR numbered.reportType = :type)
                      AND (:reportedUserId = 0 OR numbered.targetUserId = :reportedUserId)
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
            @Param("reportedUserId") long reportedUserId,
            @Param("pageSize") int pageSize,
            @Param("pageOffset") long pageOffset
    );

    // 탭 건수는 검색어와 게시자 필터만 반영한다.
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
                    WHERE (:reportedUserId = 0 OR merged.targetUserId = :reportedUserId)
                      AND (
                          :keyword = ''
                          OR LOWER(COALESCE(target_user.name, '탈퇴회원')) LIKE :keyword ESCAPE '!'
                          OR LOWER(reporter_user.name) LIKE :keyword ESCAPE '!'
                      )
                    GROUP BY merged.reportType
                    """,
            nativeQuery = true
    )
    List<ReportTypeCount> countByType(
            @Param("keyword") String keyword,
            @Param("reportedUserId") long reportedUserId
    );

    // 목록과 같은 정렬 기준(접수일시, 유형, ID)으로 전체 접수 순번을 구한다.
    @Query(
            value = """
                    SELECT numbered.rowNo
                    FROM (
                        SELECT merged.reportType,
                               merged.reportId,
                               ROW_NUMBER() OVER (
                                   ORDER BY merged.createdAt, merged.reportType, merged.reportId
                               ) AS rowNo
                        FROM (
                            SELECT 'PHOTO' AS reportType,
                                   fpr.family_photo_report_id AS reportId,
                                   fpr.created_at AS createdAt
                            FROM family_photo_report fpr
                            UNION ALL
                            SELECT 'USER',
                                   fur.family_user_report_id,
                                   fur.created_at
                            FROM family_user_report fur
                        ) merged
                    ) numbered
                    WHERE numbered.reportType = :type
                      AND numbered.reportId = :reportId
                    """,
            nativeQuery = true
    )
    long findReportNumber(
            @Param("type") String type,
            @Param("reportId") Long reportId
    );

    // 게시자가 받은 전체 신고 수 (게시물 신고 + 게시자 신고)
    @Query(
            value = """
                    SELECT (SELECT COUNT(*)
                            FROM family_photo_report fpr
                            WHERE fpr.reported_user_id = :userId)
                         + (SELECT COUNT(*)
                            FROM family_user_report fur
                            WHERE fur.reported_user_id = :userId)
                    """,
            nativeQuery = true
    )
    long countReportsOnUser(@Param("userId") Long userId);

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
                        SELECT reported.reported_user_id
                        FROM (
                            SELECT fpr.reported_user_id
                            FROM family_photo_report fpr
                            WHERE fpr.reported_user_id IS NOT NULL
                            UNION ALL
                            SELECT fur.reported_user_id
                            FROM family_user_report fur
                        ) reported
                        GROUP BY reported.reported_user_id
                        HAVING COUNT(*) >= :threshold
                    ) repeated_user
                    """,
            nativeQuery = true
    )
    long countRepeatedReportedUsers(@Param("threshold") long threshold);

    // 게시자가 받은 신고 이력 (게시물 신고 + 게시자 신고). 최신순이며 같은 시각이면 목록과 같은 순서로 정렬한다.
    @Query(
            value = """
                    SELECT merged.reportType AS reportType,
                           merged.reportId AS reportId,
                           merged.reason AS reason,
                           reporter_user.name AS reporterName,
                           reporter_user.status AS reporterStatus,
                           merged.createdAt AS createdAt
                    FROM (
                        SELECT 'PHOTO' AS reportType,
                               fpr.family_photo_report_id AS reportId,
                               fpr.reporter_user_id AS reporterUserId,
                               fpr.reason AS reason,
                               fpr.created_at AS createdAt
                        FROM family_photo_report fpr
                        WHERE fpr.reported_user_id = :userId
                        UNION ALL
                        SELECT 'USER',
                               fur.family_user_report_id,
                               fur.reporter_user_id,
                               fur.reason,
                               fur.created_at
                        FROM family_user_report fur
                        WHERE fur.reported_user_id = :userId
                    ) merged
                    LEFT JOIN users reporter_user
                           ON reporter_user.users_id = merged.reporterUserId
                    ORDER BY merged.createdAt DESC, merged.reportType DESC, merged.reportId DESC
                    """,
            nativeQuery = true
    )
    List<ReportHistoryRow> findReportHistoryOnUser(@Param("userId") Long userId);

    interface ReportHistoryRow {

        AdminReportType getReportType();

        Long getReportId();

        PhotoReportReason getReason();

        String getReporterName();

        UserStatus getReporterStatus();

        LocalDateTime getCreatedAt();
    }

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
