package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.example.senioron.domain.family.dto.response.AdminPhotoReportDetailResponse;
import com.example.senioron.domain.family.dto.response.AdminReportListItemResponse;
import com.example.senioron.domain.family.dto.response.AdminReportListResponse;
import com.example.senioron.domain.family.dto.response.AdminReportStatsResponse;
import com.example.senioron.domain.family.dto.response.AdminUserReportDetailResponse;
import com.example.senioron.domain.family.entity.AdminReportType;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoReport;
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.family.entity.FamilyUserReport;
import com.example.senioron.domain.family.entity.PhotoReportReason;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoReportRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.FamilyUserReportRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private SeniorRepository seniorRepository;
    @Autowired private FamilyPhotoSeniorRepository familyPhotoSeniorRepository;
    // 로컬 테스트에는 AWS 자격 증명이 없어 presigned URL 생성을 대체한다.
    @MockitoBean private S3Service s3Service;

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

        AdminReportListResponse all = service.getReports(null, null, null, 0, 10);
        AdminReportListResponse users = service.getReports("USER", null, null, 0, 10);

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

        assertThat(service.getReports(null, null, null, 0, 10).content())
                .extracting(AdminReportListItemResponse::reportType)
                .containsExactly(AdminReportType.USER, AdminReportType.PHOTO);
    }

    @Test
    void 원본_이름으로_검색하고_신고자는_마스킹한다() {
        savePhotoReport("a.jpg", reporter, uploader, day(1));

        AdminReportListResponse byReporter = service.getReports(null, " 박지훈 ", null, 0, 10);
        AdminReportListResponse byTarget = service.getReports(null, "김지", null, 0, 10);

        assertThat(byReporter.content()).hasSize(1);
        assertThat(byReporter.content().get(0).reporterName()).isEqualTo("박*훈");
        assertThat(byTarget.content()).hasSize(1);
        assertThat(service.getReports(null, "박*훈", null, 0, 10).content()).isEmpty();
    }

    @Test
    void 검색어의_와일드카드는_일반_문자로_찾는다() {
        savePhotoReport("a.jpg", reporter, uploader, day(1));
        User percentName = saveUser("김%수", UserStatus.ACTIVE);
        savePhotoReport("b.jpg", percentName, uploader, day(2));

        assertThat(service.getReports(null, "%", null, 0, 10).totalElements()).isEqualTo(1);
        assertThat(service.getReports(null, "_", null, 0, 10).totalElements()).isZero();
    }

    @Test
    void 삭제된_사진도_목록에_남고_image_key로_누적을_센다() {
        saveDeletedPhotoReport("a.jpg", reporter, uploader, day(1));
        savePhotoReport("a.jpg", saveUser("이민수", UserStatus.ACTIVE), uploader, day(2));

        List<AdminReportListItemResponse> content =
                service.getReports(null, null, null, 0, 10).content();

        assertThat(content).extracting(AdminReportListItemResponse::cumulativeCount)
                .containsExactly(2L, 2L);
        assertThat(content.get(0).photoUploadedAt()).isNotNull();
        assertThat(content.get(1).photoUploadedAt()).isNull();
    }

    @Test
    void 게시자_신고_3회부터_강조한다() {
        saveUserReport(reporter, uploader, day(1));
        saveUserReport(saveUser("이민수", UserStatus.ACTIVE), uploader, day(2));

        assertThat(service.getReports("USER", null, null, 0, 10).content())
                .extracting(AdminReportListItemResponse::highlighted)
                .containsOnly(false);

        saveUserReport(saveUser("정수아", UserStatus.ACTIVE), uploader, day(3));

        assertThat(service.getReports("USER", null, null, 0, 10).content())
                .extracting(AdminReportListItemResponse::highlighted)
                .containsOnly(true);
    }

    @Test
    void 탈퇴_회원과_게시자_없음을_탈퇴회원으로_표시한다() {
        User withdrawn = saveUser("탈퇴회원", UserStatus.WITHDRAWN);
        savePhotoReport("a.jpg", withdrawn, null, day(1));

        AdminReportListResponse result = service.getReports(null, "탈퇴회원", null, 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).targetName()).isEqualTo("탈퇴회원");
        assertThat(result.content().get(0).reporterName()).isEqualTo("탈퇴회원");
    }

    @Test
    void 페이지_범위를_나눠서_반환한다() {
        savePhotoReport("a.jpg", reporter, uploader, day(1));
        savePhotoReport("b.jpg", reporter, uploader, day(2));
        savePhotoReport("c.jpg", reporter, uploader, day(3));

        AdminReportListResponse second = service.getReports(null, null, null, 1, 2);
        AdminReportListResponse overflow = service.getReports(null, null, null, 5, 2);

        assertThat(second.content()).extracting(AdminReportListItemResponse::number)
                .containsExactly(1L);
        assertThat(second.totalPages()).isEqualTo(2);
        assertThat(overflow.content()).isEmpty();
        assertThat(overflow.totalElements()).isEqualTo(3);
    }

    @Test
    void 신고가_없으면_빈_결과와_0건을_반환한다() {
        AdminReportListResponse result = service.getReports(null, null, null, 0, 10);
        AdminReportStatsResponse stats = service.calculateStats(ZonedDateTime.now(SEOUL));

        assertThat(result.content()).isEmpty();
        assertThat(result.counts().total()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(stats.totalCount()).isZero();
        assertThat(stats.repeatedReportedUserCount()).isZero();
    }

    @Test
    void 잘못된_요청은_거절한다() {
        assertThatThrownBy(() -> service.getReports("ETC", null, null, 0, 10))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getReports(null, null, null, -1, 10))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getReports(null, null, null, 0, 0))
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

    @Test
    void 게시물_신고_상세에_사진_신고정보_누적을_담는다() {
        Family family = saveFamily("A3F9-K2M7");
        saveMember(uploader, family, ManagerType.PRIMARY);
        saveMember(reporter, family, ManagerType.SUB);
        Senior senior = saveSenior(family);

        saveUserReport(reporter, uploader, day(1));
        FamilyPhoto photo = savePhoto("a.jpg");
        Long reportId = savePhotoReportWithContext(
                photo, "a.jpg", reporter, uploader, senior, day(1).minusDays(10), day(2), true
        );
        savePhotoReport("b.jpg", saveUser("이민수", UserStatus.ACTIVE), uploader, day(3));
        entityManager.clear();

        given(s3Service.getFileUrl("a.jpg")).willReturn("https://cdn.example.com/a.jpg");

        AdminPhotoReportDetailResponse detail = service.getPhotoReportDetail(reportId);

        assertThat(detail.number()).isEqualTo(2);
        assertThat(detail.reportType()).isEqualTo(AdminReportType.PHOTO);

        assertThat(detail.photo().imageUrl()).isEqualTo("https://cdn.example.com/a.jpg");
        assertThat(detail.photo().deleted()).isFalse();
        assertThat(detail.photo().reportedUserId()).isEqualTo(uploader.getUsersId());
        assertThat(detail.photo().uploaderName()).isEqualTo("김지은");
        assertThat(detail.photo().uploaderManagerType()).isEqualTo(ManagerType.PRIMARY);
        assertThat(detail.photo().uploadedAt()).isEqualTo(day(1).minusDays(10));
        assertThat(detail.photo().seniorCode()).isEqualTo("A3F9-K2M7");

        assertThat(detail.report().reportId()).isEqualTo(reportId);
        assertThat(detail.report().reason()).isEqualTo(PhotoReportReason.ADVERTISEMENT);
        assertThat(detail.report().detail()).isEqualTo("광고성 사진이 계속 올라와요.");
        assertThat(detail.report().reporterName()).isEqualTo("박*훈");
        assertThat(detail.report().reporterManagerType()).isEqualTo(ManagerType.SUB);
        assertThat(detail.report().createdAt()).isEqualTo(day(2));
        assertThat(detail.report().hidden()).isTrue();

        // 이 게시물 1회, 게시자 누적은 게시자 신고 1 + 게시물 신고 2 = 3회
        assertThat(detail.cumulative().photoReportCount()).isEqualTo(1);
        assertThat(detail.cumulative().reportedUserReportCount()).isEqualTo(3);
        assertThat(detail.cumulative().highlighted()).isTrue();
    }

    @Test
    void 신고_기준_시니어는_신고자가_주담당자인_시니어를_우선한다() {
        Family subFamily = saveFamily("SUB0-0001");
        Family primaryFamily = saveFamily("PRI0-0001");
        Family otherFamily = saveFamily("OTH0-0001");
        saveMember(reporter, subFamily, ManagerType.SUB);
        saveMember(reporter, primaryFamily, ManagerType.PRIMARY);
        Senior subSenior = saveSenior(subFamily);
        Senior primarySenior = saveSenior(primaryFamily);
        Senior otherSenior = saveSenior(otherFamily);

        FamilyPhoto photo = savePhoto("a.jpg");
        // 신고자가 속하지 않은 시니어 → 보조담당 시니어 → 주담당 시니어 순으로 공유
        shareTo(photo, otherSenior);
        shareTo(photo, subSenior);
        shareTo(photo, primarySenior);

        List<Senior> seniors = familyPhotoSeniorRepository.findReportContextSeniors(
                photo, reporter, ManagerType.PRIMARY, PageRequest.of(0, 1)
        );

        assertThat(seniors).extracting(Senior::getSeniorId)
                .containsExactly(primarySenior.getSeniorId());
    }

    @Test
    void 신고_화면의_시니어는_공유받았고_신고자가_가족일_때만_사용한다() {
        Family subFamily = saveFamily("SUB0-0002");
        Family notSharedFamily = saveFamily("NSH0-0002");
        Family notMemberFamily = saveFamily("NMB0-0002");
        saveMember(reporter, subFamily, ManagerType.SUB);
        saveMember(reporter, notSharedFamily, ManagerType.PRIMARY);
        Senior subSenior = saveSenior(subFamily);
        Senior notSharedSenior = saveSenior(notSharedFamily);
        Senior notMemberSenior = saveSenior(notMemberFamily);

        FamilyPhoto photo = savePhoto("a.jpg");
        shareTo(photo, subSenior);
        shareTo(photo, notMemberSenior);

        assertThat(familyPhotoSeniorRepository.findReportContextSenior(
                photo, reporter, subSenior.getSeniorId()))
                .map(Senior::getSeniorId)
                .contains(subSenior.getSeniorId());
        assertThat(familyPhotoSeniorRepository.findReportContextSenior(
                photo, reporter, notSharedSenior.getSeniorId()))
                .isEmpty();
        assertThat(familyPhotoSeniorRepository.findReportContextSenior(
                photo, reporter, notMemberSenior.getSeniorId()))
                .isEmpty();
    }

    @Test
    void 같은_사진의_신고는_신고마다_따로_조회한다() {
        FamilyPhoto photo = savePhoto("a.jpg");
        Long first = savePhotoReportWithContext(
                photo, "a.jpg", reporter, uploader, null, day(1), day(1), false
        );
        Long second = savePhotoReportWithContext(
                photo, "a.jpg", saveUser("이민수", UserStatus.ACTIVE), uploader, null, day(1), day(2), true
        );
        entityManager.clear();

        AdminPhotoReportDetailResponse firstDetail = service.getPhotoReportDetail(first);
        AdminPhotoReportDetailResponse secondDetail = service.getPhotoReportDetail(second);

        assertThat(firstDetail.report().reporterName()).isEqualTo("박*훈");
        assertThat(firstDetail.report().hidden()).isFalse();
        assertThat(secondDetail.report().reporterName()).isEqualTo("이*수");
        assertThat(secondDetail.report().hidden()).isTrue();
        assertThat(firstDetail.cumulative().photoReportCount()).isEqualTo(2);
        assertThat(secondDetail.cumulative().photoReportCount()).isEqualTo(2);
        assertThat(firstDetail.cumulative().highlighted()).isFalse();
    }

    @Test
    void 삭제된_사진과_시니어_없는_신고도_상세를_조회한다() {
        Long reportId = savePhotoReportWithContext(
                null, "a.jpg", reporter, uploader, null, day(1), day(2), false
        );
        entityManager.clear();

        AdminPhotoReportDetailResponse detail = service.getPhotoReportDetail(reportId);

        assertThat(detail.photo().deleted()).isTrue();
        assertThat(detail.photo().uploadedAt()).isEqualTo(day(1));
        assertThat(detail.photo().seniorCode()).isNull();
        assertThat(detail.photo().uploaderManagerType()).isNull();
        assertThat(detail.report().reporterManagerType()).isNull();
    }

    @Test
    void 게시자가_없으면_탈퇴회원으로_표시하고_누적은_0이다() {
        Long reportId = savePhotoReportWithContext(
                null, "a.jpg", reporter, null, null, null, day(1), false
        );
        entityManager.clear();

        AdminPhotoReportDetailResponse detail = service.getPhotoReportDetail(reportId);

        assertThat(detail.photo().uploaderName()).isEqualTo("탈퇴회원");
        assertThat(detail.photo().reportedUserId()).isNull();
        assertThat(detail.photo().uploadedAt()).isNull();
        assertThat(detail.cumulative().reportedUserReportCount()).isZero();
        assertThat(detail.cumulative().highlighted()).isFalse();
    }

    @Test
    void 없는_신고_상세는_찾을_수_없다() {
        assertThatThrownBy(() -> service.getPhotoReportDetail(Long.MAX_VALUE))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.ADMIN_REPORT_NOT_FOUND);
    }

    @Test
    void 게시자_ID로_해당_게시자가_받은_신고만_조회한다() {
        User other = saveUser("최하나", UserStatus.ACTIVE);
        savePhotoReport("a.jpg", reporter, uploader, day(1));
        saveUserReport(reporter, uploader, day(2));
        savePhotoReport("b.jpg", reporter, other, day(3));
        saveUserReport(reporter, other, day(4));

        AdminReportListResponse all = service.getReports(null, null, uploader.getUsersId(), 0, 10);
        AdminReportListResponse photos = service.getReports("PHOTO", null, uploader.getUsersId(), 0, 10);

        assertThat(all.content()).extracting(AdminReportListItemResponse::number)
                .containsExactly(2L, 1L);
        assertThat(all.counts().total()).isEqualTo(2);
        assertThat(all.counts().photo()).isEqualTo(1);
        assertThat(all.counts().user()).isEqualTo(1);
        assertThat(photos.content()).extracting(AdminReportListItemResponse::number)
                .containsExactly(1L);
        assertThatThrownBy(() -> service.getReports(null, null, 0L, 0, 10))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 게시자_누적과_통계는_게시물_신고와_게시자_신고를_합산한다() {
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 6, 12, 0, 0, 0, SEOUL);
        savePhotoReport("a.jpg", reporter, uploader, storage(now.minusDays(3)));
        savePhotoReport("b.jpg", reporter, uploader, storage(now.minusDays(2)));
        saveUserReport(reporter, uploader, storage(now.minusDays(1)));

        AdminReportStatsResponse stats = service.calculateStats(now);
        List<AdminReportListItemResponse> users =
                service.getReports("USER", null, null, 0, 10).content();
        List<AdminReportListItemResponse> photos =
                service.getReports("PHOTO", null, null, 0, 10).content();

        assertThat(stats.repeatedReportedUserCount()).isEqualTo(1);
        assertThat(users).extracting(AdminReportListItemResponse::cumulativeCount)
                .containsExactly(3L);
        assertThat(users).extracting(AdminReportListItemResponse::highlighted)
                .containsExactly(true);
        // 게시물 신고 행은 같은 사진 기준이다.
        assertThat(photos).extracting(AdminReportListItemResponse::cumulativeCount)
                .containsExactly(1L, 1L);
    }

    @Test
    void 게시자_신고_상세에_게시자_최근사진_신고정보_이력을_담는다() {
        Family family = saveFamily("A3F9-K2M7");
        saveMember(uploader, family, ManagerType.PRIMARY);
        saveMember(reporter, family, ManagerType.SUB);
        Senior senior = saveSenior(family);

        // 게시물 신고 1건(사진 1장 생성) + 게시자 신고 2건
        savePhotoReport("old.jpg", saveUser("최현수", UserStatus.ACTIVE), uploader, day(1));
        saveUserReport(saveUser("이민수", UserStatus.ACTIVE), uploader, day(2));
        Long reportId = saveUserReportWithContext(reporter, uploader, senior, day(4), true);

        // 사진 5장 추가 (전체 6장). 마지막 사진만 썸네일이 있다.
        List<FamilyPhoto> recent = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            recent.add(familyPhotoRepository.saveAndFlush(FamilyPhoto.builder()
                    .user(uploader)
                    .imageKey("p" + i + ".jpg")
                    .thumbnailKey(i == 5 ? "p5-thumb.jpg" : null)
                    .build()));
        }
        entityManager.clear();

        given(s3Service.getFileUrl(anyString()))
                .willAnswer(invocation -> "https://cdn.example.com/" + invocation.getArgument(0));

        AdminUserReportDetailResponse detail = service.getUserReportDetail(reportId);

        assertThat(detail.number()).isEqualTo(3);
        assertThat(detail.reportType()).isEqualTo(AdminReportType.USER);

        assertThat(detail.reportedUser().userId()).isEqualTo(uploader.getUsersId());
        assertThat(detail.reportedUser().name()).isEqualTo("김지은");
        assertThat(detail.reportedUser().managerType()).isEqualTo(ManagerType.PRIMARY);
        assertThat(detail.reportedUser().seniorCode()).isEqualTo("A3F9-K2M7");
        assertThat(detail.reportedUser().reportCount()).isEqualTo(3);
        assertThat(detail.reportedUser().highlighted()).isTrue();
        assertThat(detail.reportedUser().photoCount()).isEqualTo(6);
        assertThat(detail.reportedUser().joinedAt()).isNotNull();

        assertThat(detail.recentPhotos())
                .extracting(AdminUserReportDetailResponse.RecentPhoto::familyPhotoId)
                .containsExactly(
                        recent.get(4).getFamilyPhotoId(),
                        recent.get(3).getFamilyPhotoId(),
                        recent.get(2).getFamilyPhotoId(),
                        recent.get(1).getFamilyPhotoId(),
                        recent.get(0).getFamilyPhotoId()
                );
        assertThat(detail.recentPhotos().get(0).thumbnailUrl())
                .isEqualTo("https://cdn.example.com/p5-thumb.jpg");
        assertThat(detail.recentPhotos().get(1).thumbnailUrl())
                .isEqualTo("https://cdn.example.com/p4.jpg");

        assertThat(detail.report().reportId()).isEqualTo(reportId);
        assertThat(detail.report().reason()).isEqualTo(PhotoReportReason.ABUSE);
        assertThat(detail.report().detail()).isEqualTo("불쾌한 문구가 담긴 사진을 계속 올려요.");
        assertThat(detail.report().reporterName()).isEqualTo("박*훈");
        assertThat(detail.report().reporterManagerType()).isEqualTo(ManagerType.SUB);
        assertThat(detail.report().createdAt()).isEqualTo(day(4));
        assertThat(detail.report().hidden()).isTrue();

        // 이력은 게시물 신고 + 게시자 신고를 최신순으로
        assertThat(detail.histories())
                .extracting(AdminUserReportDetailResponse.History::reportType)
                .containsExactly(AdminReportType.USER, AdminReportType.USER, AdminReportType.PHOTO);
        assertThat(detail.histories())
                .extracting(AdminUserReportDetailResponse.History::reporterName)
                .containsExactly("박*훈", "이*수", "최*수");
        assertThat(detail.histories())
                .extracting(AdminUserReportDetailResponse.History::createdAt)
                .containsExactly(day(4), day(2), day(1));
    }

    @Test
    void 시니어_없는_게시자_신고는_역할과_시니어_코드가_비어있다() {
        User withdrawn = saveUser("탈퇴회원", UserStatus.WITHDRAWN);
        Long reportId = saveUserReport(withdrawn, uploader, day(1));
        entityManager.clear();

        AdminUserReportDetailResponse detail = service.getUserReportDetail(reportId);

        assertThat(detail.reportedUser().managerType()).isNull();
        assertThat(detail.reportedUser().seniorCode()).isNull();
        assertThat(detail.reportedUser().reportCount()).isEqualTo(1);
        assertThat(detail.reportedUser().highlighted()).isFalse();
        assertThat(detail.reportedUser().photoCount()).isZero();
        assertThat(detail.recentPhotos()).isEmpty();
        assertThat(detail.report().reporterName()).isEqualTo("탈퇴회원");
        assertThat(detail.report().reporterManagerType()).isNull();
        assertThat(detail.histories())
                .extracting(AdminUserReportDetailResponse.History::reporterName)
                .containsExactly("탈퇴회원");
    }

    @Test
    void 게시자_신고_이력은_다른_게시자의_신고를_포함하지_않는다() {
        User other = saveUser("최하나", UserStatus.ACTIVE);
        Long reportId = saveUserReport(reporter, uploader, day(1));
        saveUserReport(reporter, other, day(2));
        savePhotoReport("a.jpg", reporter, other, day(3));
        entityManager.clear();

        AdminUserReportDetailResponse detail = service.getUserReportDetail(reportId);

        assertThat(detail.histories()).hasSize(1);
        assertThat(detail.reportedUser().reportCount()).isEqualTo(1);
    }

    @Test
    void 없는_게시자_신고_상세는_찾을_수_없다() {
        assertThatThrownBy(() -> service.getUserReportDetail(Long.MAX_VALUE))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.ADMIN_REPORT_NOT_FOUND);
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

    private Long saveUserReport(User reportReporter, User target, LocalDateTime createdAt) {
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

        return report.getId();
    }

    private Long saveUserReportWithContext(
            User reportReporter,
            User target,
            Senior senior,
            LocalDateTime createdAt,
            boolean hidden
    ) {
        FamilyUserReport report = userReportRepository.saveAndFlush(FamilyUserReport.builder()
                .reporter(reportReporter)
                .reportedUser(target)
                .senior(senior)
                .imageKey("user-report.jpg")
                .reason(PhotoReportReason.ABUSE)
                .detail("불쾌한 문구가 담긴 사진을 계속 올려요.")
                .hidden(hidden)
                .status(PhotoReportStatus.PENDING)
                .build());

        updateCreatedAt(
                "UPDATE family_user_report SET created_at = :createdAt"
                        + " WHERE family_user_report_id = :id",
                report.getId(),
                createdAt
        );

        return report.getId();
    }

    private FamilyPhoto savePhoto(String imageKey) {
        return familyPhotoRepository.saveAndFlush(FamilyPhoto.builder()
                .user(uploader)
                .imageKey(imageKey)
                .build());
    }

    private Long savePhotoReportWithContext(
            FamilyPhoto photo,
            String imageKey,
            User reportReporter,
            User target,
            Senior senior,
            LocalDateTime photoUploadedAt,
            LocalDateTime createdAt,
            boolean hidden
    ) {
        FamilyPhotoReport report = photoReportRepository.saveAndFlush(FamilyPhotoReport.builder()
                .familyPhoto(photo)
                .reporter(reportReporter)
                .reportedUser(target)
                .senior(senior)
                .photoUploadedAt(photoUploadedAt)
                .imageKey(imageKey)
                .reason(PhotoReportReason.ADVERTISEMENT)
                .detail("광고성 사진이 계속 올라와요.")
                .hidden(hidden)
                .status(PhotoReportStatus.PENDING)
                .build());

        updateCreatedAt(
                "UPDATE family_photo_report SET created_at = :createdAt"
                        + " WHERE family_photo_report_id = :id",
                report.getId(),
                createdAt
        );

        return report.getId();
    }

    private void shareTo(FamilyPhoto photo, Senior senior) {
        familyPhotoSeniorRepository.saveAndFlush(FamilyPhotoSenior.builder()
                .familyPhoto(photo)
                .senior(senior)
                .build());
    }

    private Family saveFamily(String seniorCode) {
        return familyRepository.saveAndFlush(Family.builder().seniorCode(seniorCode).build());
    }

    private void saveMember(User user, Family family, ManagerType managerType) {
        familyMemberRepository.saveAndFlush(FamilyMember.builder()
                .user(user)
                .family(family)
                .managerType(managerType)
                .build());
    }

    private Senior saveSenior(Family family) {
        return seniorRepository.saveAndFlush(Senior.builder()
                .name("김순자")
                .family(family)
                .registeredBy(uploader)
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .build());
    }

    // created_at은 감사 기능이 채우고 수정할 수 없으므로 테스트 시각은 SQL로 지정한다.
    private void updateCreatedAt(String sql, Long id, LocalDateTime createdAt) {
        entityManager.createNativeQuery(sql)
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }
}
