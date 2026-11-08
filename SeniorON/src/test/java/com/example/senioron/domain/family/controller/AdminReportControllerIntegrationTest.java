package com.example.senioron.domain.family.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.entity.AdminStatus;
import com.example.senioron.domain.admin.repository.AdminRepository;
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
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.jwt.JwtUtil;
import com.example.senioron.global.storage.S3Service;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AdminReportControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext context;
    @Autowired private AdminRepository adminRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FamilyPhotoRepository familyPhotoRepository;
    @Autowired private FamilyPhotoReportRepository photoReportRepository;
    @Autowired private FamilyUserReportRepository userReportRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private EntityManager entityManager;
    // 로컬 테스트에는 AWS 자격 증명이 없어 presigned URL 생성을 대체한다.
    @MockitoBean private S3Service s3Service;

    private String adminToken;
    private FamilyPhoto photo;
    private User uploader;
    private Long photoReportId;
    private Long userReportId;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();

        photoReportRepository.deleteAllInBatch();
        userReportRepository.deleteAllInBatch();

        Admin admin = adminRepository.saveAndFlush(Admin.builder()
                .email("report-admin@example.com")
                .password("encoded")
                .name("관리자")
                .adminRole(AdminRole.SUPER)
                .status(AdminStatus.ACTIVE)
                .build());
        adminToken = jwtUtil.createAdminAccessToken(admin);

        uploader = saveUser("uploader", "김지은");
        User first = saveUser("first", "박지훈");
        User second = saveUser("second", "이민수");
        User third = saveUser("third", "정수아");

        photo = familyPhotoRepository.saveAndFlush(FamilyPhoto.builder()
                .user(uploader)
                .imageKey("report-review.jpg")
                .build());

        photoReportId = photoReportRepository.saveAndFlush(FamilyPhotoReport.builder()
                .familyPhoto(photo)
                .reporter(first)
                .reportedUser(uploader)
                .imageKey(photo.getImageKey())
                .reason(PhotoReportReason.ADVERTISEMENT)
                .status(PhotoReportStatus.PENDING)
                .build()).getId();

        for (User reporter : new User[]{first, second, third}) {
            Long savedId = userReportRepository.saveAndFlush(FamilyUserReport.builder()
                    .reporter(reporter)
                    .reportedUser(uploader)
                    .familyPhoto(photo)
                    .imageKey(photo.getImageKey())
                    .reason(PhotoReportReason.ABUSE)
                    .status(PhotoReportStatus.PENDING)
                    .build()).getId();

            if (userReportId == null) {
                userReportId = savedId;
            }
        }

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 토큰이_없거나_사용자_토큰이면_거절한다() throws Exception {
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isUnauthorized());

        User user = User.builder()
                .usersId(999L)
                .loginId("report-user")
                .email("report-user@example.com")
                .password("encoded")
                .name("사용자")
                .role(Role.PARENT)
                .build();

        mockMvc.perform(get("/api/admin/reports/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.createAccessToken(user)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 목록을_번호_마스킹_누적_강조와_함께_응답한다() throws Exception {
        String body = mockMvc.perform(authGet("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.total").value(4))
                .andExpect(jsonPath("$.data.counts.photo").value(1))
                .andExpect(jsonPath("$.data.counts.user").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[0].number").value(4))
                .andExpect(jsonPath("$.data.content[0].reportType").value("USER"))
                .andExpect(jsonPath("$.data.content[0].cumulativeCount").value(4))
                .andExpect(jsonPath("$.data.content[0].highlighted").value(true))
                .andExpect(jsonPath("$.data.content[0].targetName").value("김지은"))
                .andExpect(jsonPath("$.data.content[3].reportType").value("PHOTO"))
                .andExpect(jsonPath("$.data.content[3].cumulativeCount").value(1))
                .andExpect(jsonPath("$.data.content[3].highlighted").value(false))
                .andExpect(jsonPath("$.data.content[3].reporterName").value("박*훈"))
                .andExpect(jsonPath("$.data.content[3].photoUploadedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("박지훈", "이민수", "정수아");
    }

    @Test
    void 탭과_검색을_적용해도_번호가_유지된다() throws Exception {
        mockMvc.perform(authGet("/api/admin/reports").param("type", "PHOTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.counts.total").value(4))
                .andExpect(jsonPath("$.data.content[0].number").value(1));

        mockMvc.perform(authGet("/api/admin/reports").param("keyword", "정수아"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.total").value(1))
                .andExpect(jsonPath("$.data.counts.photo").value(0))
                .andExpect(jsonPath("$.data.content[0].reporterName").value("정*아"));

        mockMvc.perform(authGet("/api/admin/reports").param("type", "ETC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 사진을_삭제해도_신고는_남고_업로드일만_비워진다() throws Exception {
        familyPhotoRepository.deleteById(photo.getFamilyPhotoId());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(authGet("/api/admin/reports").param("type", "PHOTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].targetName").value("김지은"))
                .andExpect(jsonPath("$.data.content[0].cumulativeCount").value(1))
                .andExpect(jsonPath("$.data.content[0].photoUploadedAt").value(nullValue()));
    }

    @Test
    void 게시물_신고_상세를_응답한다() throws Exception {
        given(s3Service.getFileUrl("report-review.jpg"))
                .willReturn("https://cdn.example.com/report-review.jpg");

        String body = mockMvc.perform(authGet("/api/admin/reports/photos/" + photoReportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.reportType").value("PHOTO"))
                .andExpect(jsonPath("$.data.photo.imageUrl").value("https://cdn.example.com/report-review.jpg"))
                .andExpect(jsonPath("$.data.photo.deleted").value(false))
                .andExpect(jsonPath("$.data.photo.reportedUserId").value(uploader.getUsersId()))
                .andExpect(jsonPath("$.data.photo.uploaderName").value("김지은"))
                .andExpect(jsonPath("$.data.photo.seniorCode").value(nullValue()))
                .andExpect(jsonPath("$.data.report.reportId").value(photoReportId))
                .andExpect(jsonPath("$.data.report.reason").value("ADVERTISEMENT"))
                .andExpect(jsonPath("$.data.report.reporterName").value("박*훈"))
                .andExpect(jsonPath("$.data.cumulative.photoReportCount").value(1))
                .andExpect(jsonPath("$.data.cumulative.reportedUserReportCount").value(4))
                .andExpect(jsonPath("$.data.cumulative.highlighted").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("박지훈");

        mockMvc.perform(authGet("/api/admin/reports/photos/" + Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void 게시자_신고_상세를_응답한다() throws Exception {
        given(s3Service.getFileUrl("report-review.jpg"))
                .willReturn("https://cdn.example.com/report-review.jpg");

        String body = mockMvc.perform(authGet("/api/admin/reports/users/" + userReportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.reportType").value("USER"))
                .andExpect(jsonPath("$.data.reportedUser.userId").value(uploader.getUsersId()))
                .andExpect(jsonPath("$.data.reportedUser.name").value("김지은"))
                .andExpect(jsonPath("$.data.reportedUser.managerType").value(nullValue()))
                .andExpect(jsonPath("$.data.reportedUser.seniorCode").value(nullValue()))
                .andExpect(jsonPath("$.data.reportedUser.reportCount").value(4))
                .andExpect(jsonPath("$.data.reportedUser.highlighted").value(true))
                .andExpect(jsonPath("$.data.reportedUser.photoCount").value(1))
                .andExpect(jsonPath("$.data.recentPhotos.length()").value(1))
                .andExpect(jsonPath("$.data.recentPhotos[0].familyPhotoId").value(photo.getFamilyPhotoId()))
                .andExpect(jsonPath("$.data.recentPhotos[0].thumbnailUrl")
                        .value("https://cdn.example.com/report-review.jpg"))
                .andExpect(jsonPath("$.data.report.reportId").value(userReportId))
                .andExpect(jsonPath("$.data.report.reason").value("ABUSE"))
                .andExpect(jsonPath("$.data.report.reporterName").value("박*훈"))
                .andExpect(jsonPath("$.data.histories.length()").value(4))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("박지훈");

        mockMvc.perform(authGet("/api/admin/reports/users/" + Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void 게시자_ID로_신고_내역을_필터링한다() throws Exception {
        User other = saveUser("other", "최하나");
        userReportRepository.saveAndFlush(FamilyUserReport.builder()
                .reporter(uploader)
                .reportedUser(other)
                .imageKey("other.jpg")
                .reason(PhotoReportReason.ABUSE)
                .status(PhotoReportStatus.PENDING)
                .build());

        mockMvc.perform(authGet("/api/admin/reports")
                        .param("reportedUserId", String.valueOf(uploader.getUsersId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.total").value(4))
                .andExpect(jsonPath("$.data.totalElements").value(4));

        mockMvc.perform(authGet("/api/admin/reports")
                        .param("reportedUserId", String.valueOf(other.getUsersId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].number").value(5));
    }

    @Test
    void 통계를_응답한다() throws Exception {
        mockMvc.perform(authGet("/api/admin/reports/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(4))
                .andExpect(jsonPath("$.data.todayCount").value(4))
                .andExpect(jsonPath("$.data.todayDiff").value(4))
                .andExpect(jsonPath("$.data.repeatedReportedUserCount").value(1))
                .andExpect(jsonPath("$.data.weekCount").value(4))
                .andExpect(jsonPath("$.data.weekDiff").value(4));
    }

    @Test
    void 목록_조회는_신고_엔티티를_메모리에_올리지_않는다() throws Exception {
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        mockMvc.perform(authGet("/api/admin/reports"))
                .andExpect(status().isOk());

        long statements = statistics.getPrepareStatementCount();
        long loadedEntities = statistics.getEntityLoadCount();
        statistics.setStatisticsEnabled(false);

        // 관리자 인증 조회 1회 + 건수 조회 1회 + 페이지 조회 1회
        assertThat(statements).isLessThanOrEqualTo(3);
        // 인증에 쓰는 관리자 엔티티만 로드한다.
        assertThat(loadedEntities).isLessThanOrEqualTo(1);
    }

    private MockHttpServletRequestBuilder authGet(String url) {
        return get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private User saveUser(String key, String name) {
        return userRepository.saveAndFlush(User.builder()
                .loginId("report-" + key)
                .email("report-" + key + "@example.com")
                .name(name)
                .role(Role.CHILD)
                .build());
    }
}
