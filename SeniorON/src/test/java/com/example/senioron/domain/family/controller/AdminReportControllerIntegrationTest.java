package com.example.senioron.domain.family.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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

    private String adminToken;
    private FamilyPhoto photo;
    private User uploader;

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

        photoReportRepository.saveAndFlush(FamilyPhotoReport.builder()
                .familyPhoto(photo)
                .reporter(first)
                .reportedUser(uploader)
                .imageKey(photo.getImageKey())
                .reason(PhotoReportReason.ADVERTISEMENT)
                .status(PhotoReportStatus.PENDING)
                .build());

        for (User reporter : new User[]{first, second, third}) {
            userReportRepository.saveAndFlush(FamilyUserReport.builder()
                    .reporter(reporter)
                    .reportedUser(uploader)
                    .familyPhoto(photo)
                    .imageKey(photo.getImageKey())
                    .reason(PhotoReportReason.ABUSE)
                    .status(PhotoReportStatus.PENDING)
                    .build());
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
                .andExpect(jsonPath("$.data.content[0].cumulativeCount").value(3))
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
