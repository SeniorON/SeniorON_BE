package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.domain.family.dto.response.AdminReportListItemResponse;
import com.example.senioron.domain.family.dto.response.AdminReportListResponse;
import com.example.senioron.domain.family.dto.response.AdminReportStatsResponse;
import com.example.senioron.domain.family.entity.AdminReportType;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoReport;
import com.example.senioron.domain.family.entity.FamilyUserReport;
import com.example.senioron.domain.family.entity.PhotoReportReason;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import com.example.senioron.domain.family.repository.FamilyPhotoReportRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyUserReportRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// 조회 로직이 네이티브 쿼리에 있으므로 실제 DB로 검증한다.
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AdminReportServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired private AdminReportService service;
    @Autowired private UserRepository userRepository;
    @Autowired private FamilyPhotoRepository familyPhotoRepository;
    @Autowired private FamilyPhotoReportRepository photoReportRepository;
    @Autowired private FamilyUserReportRepository userReportRepository;
    @Autowired private EntityManager entityManager;

    private User reporter;
    private User uploader;
    private int userSequence;

    @BeforeEach
    void setUp() {
        photoReportRepository.deleteAllInBatch();
        userReportRepository.deleteAllInBatch();

        reporter = saveUser("박지훈", UserStatus.ACTIVE);
        uploader = saveUser("김지은", UserStatus.ACTIVE);
    }

    @Test
    void 번호는_전체_접수순이고_탭을_바꿔도_유지된다() {
        savePhotoReport("a.jpg", reporter, uploader, day(1));
        savePhotoReport("b.jpg", reporter, uploader, day(3));
        saveUserReport(reporter, uploader, day(2));

        AdminReportListResponse all = service.getReports(null, null, 0, 10);
        AdminReportListResponse users = service.getReports("USER", null, 0, 10);

        assertThat(all.content()).extracting(AdminReportListItemResponse::number)
                .containsExactly(3L, 2L, 1L);
        assertThat(users.content()).extracting(AdminReportListItemResponse::number)
                .containsExactly(2L);
        assertThat(users.counts().total()).isEqualTo(3);
        assertThat(users.counts().photo()).isEqualTo(2);
        assertThat(users.counts().user()).isEqualTo(1);
        assertThat(users.totalElements()).isEqualTo(1);
    }

    @Test
    void 접수_시각이_같아도_순서가_일정하다() {
        savePhotoReport("a.jpg", reporter, uploader, day(1));
        saveUserReport(reporter, uploader, day(1));

        assertThat(service.getReports(null, null, 0, 10).content())
                .extracting(AdminReportListItemResponse::reportType)
                .containsExactly(AdminReportType.USER, AdminReportType.PHOTO);
    }

    @Test
    void 원본_이름으로_검색하고_신고자는_마스킹한다() {
        savePhotoReport("a.jpg", reporter, uploader, day(1));

        AdminReportListResponse byReporter = service.getReports(null, " 박지훈 ", 0, 10);
        AdminReportListResponse byTarget = service.getReports(null, "김지", 0, 10);

        assertThat(byReporter.content()).hasSize(1);
        assertThat(byReporter.content().get(0).reporterName()).isEqualTo("박*훈");
        assertThat(byTarget.content()).hasSize(1);
        assertThat(service.getReports(null, "박*훈", 0, 10).content()).isEmpty();
    }

    @Test
    void 검색어의_와일드카드는_일반_문자로_찾는다() {
        savePhotoReport("a.jpg", reporter, uploader, day(1));
        User percentName = saveUser("김%수", UserStatus.ACTIVE);
        savePhotoReport("b.jpg", percentName, uploader, day(2));

        assertThat(service.getReports(null, "%", 0, 10).totalElements()).isEqualTo(1);
        assertThat(service.getReports(null, "_", 0, 10).totalElements()).isZero();
    }

    @Test
    void 삭제된_사진도_목록에_남고_image_key로_누적을_센다() {
        saveDeletedPhotoReport("a.jpg", reporter, uploader, day(1));
        savePhotoReport("a.jpg", saveUser("이민수", UserStatus.ACTIVE), uploader, day(2));

        List<AdminReportListItemResponse> content =
                service.getReports(null, null, 0, 10).content();

        assertThat(content).extracting(AdminReportListItemResponse::cumulativeCount)
                .containsExactly(2L, 2L);
        assertThat(content.get(0).photoUploadedAt()).isNotNull();
        assertThat(content.get(1).photoUploadedAt()).isNull();
    }

    @Test
    void 게시자_신고_3회부터_강조한다() {
        saveUserReport(reporter, uploader, day(1));
        saveUserReport(saveUser("이민수", UserStatus.ACTIVE), uploader, day(2));

        assertThat(service.getReports("USER", null, 0, 10).content())
                .extracting(AdminReportListItemResponse::highlighted)
                .containsOnly(false);

        saveUserReport(saveUser("정수아", UserStatus.ACTIVE), uploader, day(3));

        assertThat(service.getReports("USER", null, 0, 10).content())
                .extracting(AdminReportListItemResponse::highlighted)
                .containsOnly(true);
    }

    @Test
    void 탈퇴_회원과_게시자_없음을_탈퇴회원으로_표시한다() {
        User withdrawn = saveUser("탈퇴회원", UserStatus.WITHDRAWN);
        savePhotoReport("a.jpg", withdrawn, null, day(1));

        AdminReportListResponse result = service.getReports(null, "탈퇴회원", 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).targetName()).isEqualTo("탈퇴회원");
        assertThat(result.content().get(0).reporterName()).isEqualTo("탈퇴회원");
    }

    @Test
    void 페이지_범위를_나눠서_반환한다() {
        savePhotoReport("a.jpg", reporter, uploader, day(1));
        savePhotoReport("b.jpg", reporter, uploader, day(2));
        savePhotoReport("c.jpg", reporter, uploader, day(3));

        AdminReportListResponse second = service.getReports(null, null, 1, 2);
        AdminReportListResponse overflow = service.getReports(null, null, 5, 2);

        assertThat(second.content()).extracting(AdminReportListItemResponse::number)
                .containsExactly(1L);
        assertThat(second.totalPages()).isEqualTo(2);
        assertThat(overflow.content()).isEmpty();
        assertThat(overflow.totalElements()).isEqualTo(3);
    }

    @Test
    void 신고가_없으면_빈_결과와_0건을_반환한다() {
        AdminReportListResponse result = service.getReports(null, null, 0, 10);
        AdminReportStatsResponse stats = service.calculateStats(ZonedDateTime.now(SEOUL));

        assertThat(result.content()).isEmpty();
        assertThat(result.counts().total()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(stats.totalCount()).isZero();
        assertThat(stats.repeatedReportedUserCount()).isZero();
    }

    @Test
    void 잘못된_요청은_거절한다() {
        assertThatThrownBy(() -> service.getReports("ETC", null, 0, 10))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getReports(null, null, -1, 10))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getReports(null, null, 0, 0))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 통계는_서울_기준_날짜와_같은_시점으로_비교한다() {
        // 2026-11-06은 금요일이고 이번 주 시작은 2026-11-02(월)이다.
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 6, 12, 0, 0, 0, SEOUL);

        // 오늘 2건
        savePhotoReport("a.jpg", reporter, uploader, storage(now.minusHours(1)));
        savePhotoReport("b.jpg", reporter, uploader, storage(now.withHour(0)));
        // 어제 같은 시점 전 1건, 이후 1건
        savePhotoReport("c.jpg", reporter, uploader, storage(now.minusDays(1).minusHours(1)));
        savePhotoReport("d.jpg", reporter, uploader, storage(now.minusDays(1).plusHours(1)));
        // 이번 주 시작 시각 1건
        savePhotoReport("e.jpg", reporter, uploader, storage(now.withDayOfMonth(2).withHour(0)));
        // 지난주 같은 시점 이후라 비교 대상에서 제외
        savePhotoReport("f.jpg", reporter, uploader, storage(now.minusWeeks(1).plusHours(1)));
        // 2주 전 게시자 신고 3건
        saveUserReport(reporter, uploader, storage(now.minusWeeks(2)));
        saveUserReport(saveUser("이민수", UserStatus.ACTIVE), uploader, storage(now.minusWeeks(2)));
        saveUserReport(saveUser("정수아", UserStatus.ACTIVE), uploader, storage(now.minusWeeks(2)));

        AdminReportStatsResponse stats = service.calculateStats(now);

        assertThat(stats.totalCount()).isEqualTo(9);
        assertThat(stats.todayCount()).isEqualTo(2);
        assertThat(stats.todayDiff()).isEqualTo(1);
        assertThat(stats.repeatedReportedUserCount()).isEqualTo(1);
        assertThat(stats.weekCount()).isEqualTo(5);
        assertThat(stats.weekDiff()).isEqualTo(5);
    }

    private LocalDateTime day(int day) {
        return LocalDateTime.of(2026, 11, day, 10, 0);
    }

    private LocalDateTime storage(ZonedDateTime time) {
        return time.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private User saveUser(String name, UserStatus status) {
        userSequence++;
        return userRepository.saveAndFlush(User.builder()
                .loginId("admin-report-" + userSequence)
                .email("admin-report-" + userSequence + "@example.com")
                .name(name)
                .role(Role.CHILD)
                .status(status)
                .build());
    }

    private void savePhotoReport(
            String imageKey,
            User reportReporter,
            User target,
            LocalDateTime createdAt
    ) {
        FamilyPhoto photo = familyPhotoRepository.saveAndFlush(FamilyPhoto.builder()
                .user(uploader)
                .imageKey(imageKey)
                .build());

        savePhotoReport(photo, imageKey, reportReporter, target, createdAt);
    }

    private void saveDeletedPhotoReport(
            String imageKey,
            User reportReporter,
            User target,
            LocalDateTime createdAt
    ) {
        savePhotoReport(null, imageKey, reportReporter, target, createdAt);
    }

    private void savePhotoReport(
            FamilyPhoto photo,
            String imageKey,
            User reportReporter,
            User target,
            LocalDateTime createdAt
    ) {
        FamilyPhotoReport report = photoReportRepository.saveAndFlush(FamilyPhotoReport.builder()
                .familyPhoto(photo)
                .reporter(reportReporter)
                .reportedUser(target)
                .imageKey(imageKey)
                .reason(PhotoReportReason.ETC)
                .status(PhotoReportStatus.PENDING)
                .build());

        updateCreatedAt(
                "UPDATE family_photo_report SET created_at = :createdAt"
                        + " WHERE family_photo_report_id = :id",
                report.getId(),
                createdAt
        );
    }

    private void saveUserReport(User reportReporter, User target, LocalDateTime createdAt) {
        FamilyUserReport report = userReportRepository.saveAndFlush(FamilyUserReport.builder()
                .reporter(reportReporter)
                .reportedUser(target)
                .imageKey("user-report.jpg")
                .reason(PhotoReportReason.ABUSE)
                .status(PhotoReportStatus.PENDING)
                .build());

        updateCreatedAt(
                "UPDATE family_user_report SET created_at = :createdAt"
                        + " WHERE family_user_report_id = :id",
                report.getId(),
                createdAt
        );
    }

    // created_at은 감사 기능이 채우고 수정할 수 없으므로 테스트 시각은 SQL로 지정한다.
    private void updateCreatedAt(String sql, Long id, LocalDateTime createdAt) {
        entityManager.createNativeQuery(sql)
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }
}
