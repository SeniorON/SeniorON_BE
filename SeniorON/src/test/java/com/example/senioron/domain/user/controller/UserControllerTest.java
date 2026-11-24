package com.example.senioron.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.example.senioron.domain.user.dto.response.MedicationConsentResponse;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.service.ClientIpResolver;
import com.example.senioron.domain.user.service.UserService;
import com.example.senioron.global.apiPayload.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

class UserControllerTest {

    private final UserService userService = mock(UserService.class);
    private final User user = User.builder().usersId(1L).build();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of())
        );
        mockMvc = standaloneSetup(new UserController(userService, mock(ClientIpResolver.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getsCurrentUsersMedicationConsent() throws Exception {
        given(userService.getMedicationConsent(user))
                .willReturn(MedicationConsentResponse.builder().medicationConsent(true).build());

        mockMvc.perform(get("/api/users/me/medication-consent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.medicationConsent").value(true));

        verify(userService).getMedicationConsent(user);
    }

    @Test
    void updatesCurrentUsersMedicationConsent() throws Exception {
        given(userService.updateMedicationConsent(eq(user), any()))
                .willReturn(MedicationConsentResponse.builder().medicationConsent(false).build());

        mockMvc.perform(patch("/api/users/me/medication-consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicationConsent\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.medicationConsent").value(false));

        verify(userService).updateMedicationConsent(eq(user), any());
    }

    @Test
    void rejectsMedicationConsentUpdateWhenFieldIsMissing() throws Exception {
        mockMvc.perform(patch("/api/users/me/medication-consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(userService);
    }

    @Test
    void rejectsMedicationConsentUpdateWhenFieldIsNull() throws Exception {
        mockMvc.perform(patch("/api/users/me/medication-consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicationConsent\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(userService);
    }
}
