package com.example.senioron.domain.socialaccount.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.senioron.domain.socialaccount.dto.response.SocialSignupResponse;
import com.example.senioron.domain.socialaccount.service.GoogleLoginService;
import com.example.senioron.domain.socialaccount.service.KakaoLoginService;
import com.example.senioron.domain.socialaccount.service.SocialSignupService;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.global.apiPayload.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SocialAccountControllerTest {

    private SocialSignupService socialSignupService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        socialSignupService = mock(SocialSignupService.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new SocialAccountController(
                        mock(KakaoLoginService.class),
                        mock(GoogleLoginService.class),
                        socialSignupService
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void socialSignupReturnsOkWhenRequestIsValid() throws Exception {
        when(socialSignupService.signup(any()))
                .thenReturn(SocialSignupResponse.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .usersId(1L)
                        .name("사용자")
                        .role(Role.CHILD)
                        .newUser(false)
                        .build());

        mockMvc.perform(post("/api/social-accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.role").value("CHILD"));

        verify(socialSignupService).signup(any());
    }

    @Test
    void socialSignupValidationFailureReturnsCommon400() throws Exception {
        mockMvc.perform(post("/api/social-accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "socialToken": "token",
                                  "name": "사용자",
                                  "birth": "2003-08-06",
                                  "serviceTermsAgreed": true,
                                  "privacyPolicyAgreed": true,
                                  "ageOver14Agreed": true,
                                  "marketingAgreed": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(socialSignupService);
    }

    @Test
    void socialSignupInvalidEnumReturnsCommon400() throws Exception {
        mockMvc.perform(post("/api/social-accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson().replace("\"KAKAO\"", "\"NAVER\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(socialSignupService);
    }

    @Test
    void socialSignupInvalidBirthFormatReturnsInvalidDateFormat() throws Exception {
        mockMvc.perform(post("/api/social-accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson().replace("2003-08-06", "08/06/2003")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));

        verifyNoInteractions(socialSignupService);
    }

    @Test
    void socialSignupUnexpectedSaveFailureReturnsCommon500() throws Exception {
        when(socialSignupService.signup(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate [REDACTED_EMAIL]"));

        mockMvc.perform(post("/api/social-accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON500"))
                .andExpect(jsonPath("$.message").value("서버 에러입니다. 관리자에게 문의하세요."));

        verify(socialSignupService).signup(any());
    }

    private String validRequestJson() {
        return """
                {
                  "provider": "KAKAO",
                  "socialToken": "token",
                  "name": "사용자",
                  "birth": "2003-08-06",
                  "role": "CHILD",
                  "serviceTermsAgreed": true,
                  "privacyPolicyAgreed": true,
                  "ageOver14Agreed": true,
                  "marketingAgreed": true
                }
                """;
    }
}
