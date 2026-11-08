package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.response.AdminPhotoReportDetailResponse;
import com.example.senioron.domain.family.dto.response.AdminReportCountsResponse;
import com.example.senioron.domain.family.dto.response.AdminReportListItemResponse;
import com.example.senioron.domain.family.dto.response.AdminReportListResponse;
import com.example.senioron.domain.family.dto.response.AdminReportStatsResponse;
import com.example.senioron.domain.family.entity.AdminReportType;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhotoReport;
import com.example.senioron.domain.family.repository.AdminReportQueryRepository;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoReportRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.util.UserNameMasker;
import com.example.senioron.global.storage.S3Service;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final long REPEATED_REPORT_THRESHOLD = 3;
    private static final String NO_CONDITION = "";
    private static final long NO_USER_CONDITION = 0L;
    private static final String WITHDRAWN_NAME = "탈퇴회원";

    private final AdminReportQueryRepository adminReportQueryRepository;
    private final FamilyPhotoReportRepository familyPhotoReportRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final S3Service s3Service;

    public AdminReportListResponse getReports(
            String type,
            String keyword,
            Long reportedUserId,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || (reportedUserId != null && reportedUserId < 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        AdminReportType reportType = parseType(type);
        String keywordPattern = toKeywordPattern(keyword);
        long reportedUserCondition = reportedUserId == null ? NO_USER_CONDITION : reportedUserId;

        AdminReportCountsResponse counts = getCounts(keywordPattern, reportedUserCondition);
        long totalElements = totalElementsOf(counts, reportType);

        List<AdminReportListItemResponse> content = adminReportQueryRepository
                .findReportPage(
                        reportType == null ? NO_CONDITION : reportType.name(),
                        keywordPattern,
                        reportedUserCondition,
                        size,
                        (long) page * size
                )
                .stream()
                .map(this::toResponse)
                .toList();

        return new AdminReportListResponse(
                counts,
                content,
                page,
                size,
                totalElements,
                (int) Math.ceil((double) totalElements / size)
        );
    }

    public AdminPhotoReportDetailResponse getPhotoReportDetail(Long reportId) {
        FamilyPhotoReport report = familyPhotoReportRepository
                .findDetailById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_REPORT_NOT_FOUND));

        User uploader = report.getReportedUser();
        Senior senior = report.getSenior();

        long reportedUserReportCount = uploader == null
                ? 0
                : adminReportQueryRepository.countReportsOnUser(uploader.getUsersId());

        AdminPhotoReportDetailResponse.Photo photo = new AdminPhotoReportDetailResponse.Photo(
                s3Service.getFileUrl(report.getImageKey()),
                report.getFamilyPhoto() == null,
                uploader == null ? null : uploader.getUsersId(),
                uploader == null ? WITHDRAWN_NAME : uploader.getName(),
                managerTypeOf(uploader, senior),
                uploadedAtOf(report),
                senior == null ? null : senior.getFamily().getSeniorCode()
        );

        AdminPhotoReportDetailResponse.Report reportInfo = new AdminPhotoReportDetailResponse.Report(
                report.getId(),
                report.getReason(),
                report.getDetail(),
                UserNameMasker.mask(report.getReporter()),
                managerTypeOf(report.getReporter(), senior),
                report.getCreatedAt(),
                report.isHidden()
        );

        AdminPhotoReportDetailResponse.Cumulative cumulative = new AdminPhotoReportDetailResponse.Cumulative(
                familyPhotoReportRepository.countByImageKey(report.getImageKey()),
                reportedUserReportCount,
                reportedUserReportCount >= REPEATED_REPORT_THRESHOLD
        );

        return new AdminPhotoReportDetailResponse(
                adminReportQueryRepository.findReportNumber(
                        AdminReportType.PHOTO.name(),
                        report.getId()
                ),
                AdminReportType.PHOTO,
                photo,
                reportInfo,
                cumulative
        );
    }

    public AdminReportStatsResponse getStats() {
        return calculateStats(ZonedDateTime.now(SEOUL_ZONE));
    }

    // 테스트에서 기준 시각을 고정할 수 있도록 분리했다.
    AdminReportStatsResponse calculateStats(ZonedDateTime nowInSeoul) {
        ZonedDateTime todayStart = nowInSeoul.toLocalDate().atStartOfDay(SEOUL_ZONE);
        ZonedDateTime weekStart = todayStart.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );

        AdminReportQueryRepository.ReportPeriodCounts periodCounts =
                adminReportQueryRepository.countByPeriod(
                        toStorageTime(nowInSeoul),
                        toStorageTime(todayStart),
                        toStorageTime(todayStart.minusDays(1)),
                        toStorageTime(nowInSeoul.minusDays(1)),
                        toStorageTime(weekStart),
                        toStorageTime(weekStart.minusWeeks(1)),
                        toStorageTime(nowInSeoul.minusWeeks(1))
                );

        return new AdminReportStatsResponse(
                periodCounts.getTotalCount(),
                periodCounts.getTodayCount(),
                periodCounts.getTodayCount() - periodCounts.getYesterdayCount(),
                adminReportQueryRepository.countRepeatedReportedUsers(REPEATED_REPORT_THRESHOLD),
                periodCounts.getWeekCount(),
                periodCounts.getWeekCount() - periodCounts.getLastWeekCount()
        );
    }

    // 신고 시점의 시니어 가족에서 역할을 찾는다. 시니어나 연결이 없어졌으면 null이다.
    private ManagerType managerTypeOf(User user, Senior senior) {
        if (user == null || senior == null) {
            return null;
        }

        return familyMemberRepository
                .findByUserAndFamily(user, senior.getFamily())
                .map(FamilyMember::getManagerType)
                .orElse(null);
    }

    // 업로드일 저장 전에 접수된 신고는 남아 있는 사진의 생성일을 사용한다.
    private LocalDateTime uploadedAtOf(FamilyPhotoReport report) {
        if (report.getPhotoUploadedAt() != null) {
            return report.getPhotoUploadedAt();
        }

        return report.getFamilyPhoto() == null
                ? null
                : report.getFamilyPhoto().getCreatedAt();
    }

    private AdminReportCountsResponse getCounts(String keywordPattern, long reportedUserCondition) {
        long photo = 0;
        long user = 0;

        for (AdminReportQueryRepository.ReportTypeCount count
                : adminReportQueryRepository.countByType(keywordPattern, reportedUserCondition)) {
            if (count.getReportType() == AdminReportType.PHOTO) {
                photo = count.getReportCount();
            } else {
                user = count.getReportCount();
            }
        }

        return new AdminReportCountsResponse(photo + user, photo, user);
    }

    private long totalElementsOf(AdminReportCountsResponse counts, AdminReportType reportType) {
        if (reportType == null) {
            return counts.total();
        }

        return reportType == AdminReportType.PHOTO ? counts.photo() : counts.user();
    }

    private AdminReportListItemResponse toResponse(AdminReportQueryRepository.ReportRow row) {
        return new AdminReportListItemResponse(
                row.getRowNo(),
                row.getReportType(),
                row.getReportId(),
                row.getTargetName(),
                row.getPhotoUploadedAt(),
                row.getReason(),
                UserNameMasker.mask(row.getReporterName(), row.getReporterStatus()),
                row.getCreatedAt(),
                row.getCumulativeCount(),
                row.getCumulativeCount() >= REPEATED_REPORT_THRESHOLD
        );
    }

    private LocalDateTime toStorageTime(ZonedDateTime dateTime) {
        return dateTime
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private AdminReportType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }

        try {
            return AdminReportType.valueOf(type.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    // 검색어의 %와 _도 일반 문자로 찾도록 쿼리의 ESCAPE 문자(!)로 이스케이프한다.
    private String toKeywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return NO_CONDITION;
        }

        String escaped = keyword.trim()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return "%" + escaped + "%";
    }
}
