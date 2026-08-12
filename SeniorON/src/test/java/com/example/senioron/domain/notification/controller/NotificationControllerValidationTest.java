package com.example.senioron.domain.notification.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.jwt.JwtUtil;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * size 파라미터 검증이 실제 HTTP 요청 기준으로 어떤 응답을 내는지 확인한다.
 * 서비스 유닛 테스트만으로는 이걸 검증할 수 없다 — 컨트롤러(및 그 위의
 * Bean Validation/보안 필터 체인)를 안 거치기 때문이다. 실제로 예전에는
 * 컨트롤러의 @Min/@Max(+ 클래스 레벨 @Validated)가 서비스 레이어의
 * NOTIFICATION_SIZE_OUT_OF_RANGE 체크보다 먼저 걸려서, jakarta.validation.
 * ConstraintViolationException이 GlobalExceptionHandler에서 안 잡히고
 * (거기 있는 ConstraintViolationException은 DB용인 org.hibernate 패키지 것이라
 * 이름만 같고 다른 클래스다) 500으로 새 나갔었다. 실제 서버로 재현해서 확인한
 * 뒤 컨트롤러의 @Min/@Max를 제거해 서비스 레벨 체크만 남기는 것으로 고쳤다.
 */
@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
class NotificationControllerValidationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void outOfRangeSizeReturnsNotificationSizeOutOfRangeNotServerError() throws Exception {
        User child = userRepository.save(User.builder()
                .loginId("noti-size-check-" + System.nanoTime())
                .name("검증용")
                .role(Role.CHILD)
                .status(UserStatus.ACTIVE)
                .build());

        String token = jwtUtil.createAccessToken(child);

        mockMvc.perform(get("/api/notification")
                        .param("type", "SOS")
                        .param("size", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTI400"));

        mockMvc.perform(get("/api/notification")
                        .param("type", "SOS")
                        .param("size", "51")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTI400"));
    }
}
