package com.example.senioron.domain.senior.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.example.senioron.domain.senior.dto.response.MedicationConsentConnectionStatus;
import com.example.senioron.domain.senior.dto.response.SeniorMedicationConsentResponse;
import com.example.senioron.domain.senior.service.SeniorService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

class SeniorControllerTest {

    private final SeniorService seniorService = mock(SeniorService.class);
    private final User user = User.builder().usersId(1L).build();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of())
        );
        mockMvc = standaloneSetup(new SeniorController(seniorService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getsConnectedSeniorMedicationConsentIncludingNullValue() throws Exception {
        given(seniorService.getMedicationConsent(user, 10L))
                .willReturn(SeniorMedicationConsentResponse.builder()
                        .connectionStatus(MedicationConsentConnectionStatus.CONNECTED)
                        .medicationConsent(null)
                        .build());

        mockMvc.perform(get("/api/seniors/10/medication-consent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.connectionStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.data.medicationConsent").value(nullValue()));

        verify(seniorService).getMedicationConsent(user, 10L);
    }
}
