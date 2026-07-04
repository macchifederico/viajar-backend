package ar.com.viajar.controller;

import ar.com.viajar.domain.User;
import ar.com.viajar.domain.enums.UserRole;
import ar.com.viajar.dto.response.AuthResponse;
import ar.com.viajar.dto.response.UserResponse;
import ar.com.viajar.integration.OtpService;
import ar.com.viajar.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean AuthService authService;
    @MockitoBean OtpService otpService;
    @MockitoBean ar.com.viajar.security.JwtUtil jwtUtil;

    private UserResponse mockUser() {
        return new UserResponse(UUID.randomUUID(), "Test User", "test@email.com",
                "+5491100000000", UserRole.passenger, null, 0, 0,
                null, Instant.now(), null, null, null, null, null);
    }

    @Test
    void register_returns201WithTokens() throws Exception {
        var user = mockUser();
        when(authService.register(any())).thenReturn(new AuthResponse("access-token", "refresh-token", user));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Test User",
                                  "email": "test@email.com",
                                  "phone": "+5491100000000",
                                  "password": "secret123",
                                  "role": "passenger"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void register_returns400WhenEmailMissing() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","phone":"+5491","password":"secret","role":"passenger"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void login_returns200WithTokens() throws Exception {
        var user = mockUser();
        when(authService.login(any())).thenReturn(new AuthResponse("access-token", "refresh-token", user));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@email.com","password":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    void login_returns400WhenPasswordMissing() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@email.com"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyOtp_returns400OnInvalidCode() throws Exception {
        ar.com.viajar.exception.AppException ex = ar.com.viajar.exception.AppException.badRequest("Código inválido o expirado");
        org.mockito.Mockito.doThrow(ex).when(authService).verifyOtp(any());

        // missing "code" field → validation error
        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"+5491100000000"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
