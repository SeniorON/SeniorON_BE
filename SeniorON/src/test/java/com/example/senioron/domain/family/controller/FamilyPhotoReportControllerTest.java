package com.example.senioron.domain.family.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.example.senioron.domain.family.entity.FamilyReportOutcome;
import com.example.senioron.domain.family.service.FamilyPhotoReportService;
import com.example.senioron.domain.family.service.FamilyPhotoService;
import com.example.senioron.domain.family.service.FamilyUserReportService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.apiPayload.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class FamilyPhotoReportControllerTest {

    private final FamilyPhotoReportService photoReportService = mock(FamilyPhotoReportService.class);
    private final FamilyUserReportService userReportService = mock(FamilyUserReportService.class);
    private final User reporter = User.builder().usersId(10L).build();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(reporter, null, List.of()));
        mockMvc = standaloneSetup(new FamilyPhotoController(
                mock(FamilyPhotoService.class), photoReportService, userReportService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED, false, COMMON_200, 성공적으로 처리되었습니다.",
            "ALREADY_REPORTED, false, FAMILY_PHOTO200_1, 이미 신고한 사진이에요.",
            "ALREADY_REPORTED_AND_HIDDEN, true, FAMILY_PHOTO200_2, 이미 신고한 사진이에요. 사진은 숨겼어요."
    })
    void returnsPhotoReportOutcome(FamilyReportOutcome outcome, boolean hide, String code, String message)
            throws Exception {
        given(photoReportService.reportPhoto(eq(reporter), eq(40L), any())).willReturn(outcome);

        submitReport("reports", hide)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED, false, COMMON_200, 성공적으로 처리되었습니다.",
            "ALREADY_REPORTED, false, FAMILY_PHOTO200_3, 이미 신고한 게시자예요.",
            "ALREADY_REPORTED_AND_HIDDEN, true, FAMILY_PHOTO200_4, 이미 신고한 게시자예요. 이 게시자의 사진은 숨겼어요."
    })
    void returnsUploaderReportOutcome(FamilyReportOutcome outcome, boolean hide, String code, String message)
            throws Exception {
        given(userReportService.reportUploader(eq(reporter), eq(40L), any())).willReturn(outcome);

        submitReport("uploader-reports", hide)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    @ParameterizedTest
    @CsvSource({"reports, FAMILY_PHOTO409_1", "uploader-reports, FAMILY_PHOTO409_2"})
    void returnsConflictForAlreadyHiddenTarget(String endpoint, String code) throws Exception {
        if (endpoint.equals("reports")) {
            given(photoReportService.reportPhoto(eq(reporter), eq(40L), any()))
                    .willThrow(new BusinessException(ErrorCode.FAMILY_PHOTO_ALREADY_REPORTED));
        } else {
            given(userReportService.reportUploader(eq(reporter), eq(40L), any()))
                    .willThrow(new BusinessException(ErrorCode.FAMILY_USER_ALREADY_REPORTED));
        }

        submitReport(endpoint, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code));
    }

    private ResultActions submitReport(String endpoint, boolean hide) throws Exception {
        return mockMvc.perform(post("/api/family/photos/{familyPhotoId}/" + endpoint, 40L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"ETC","hide":%s}
                        """.formatted(hide)));
    }
}
