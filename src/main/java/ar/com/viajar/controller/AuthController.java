package ar.com.viajar.controller;

import ar.com.viajar.dto.request.LoginRequest;
import ar.com.viajar.dto.request.RefreshRequest;
import ar.com.viajar.dto.request.RegisterRequest;
import ar.com.viajar.dto.request.VerifyOtpRequest;
import ar.com.viajar.dto.response.ApiResponse;
import ar.com.viajar.dto.response.AuthResponse;
import ar.com.viajar.dto.response.TokensResponse;
import ar.com.viajar.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return new ApiResponse<>(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return new ApiResponse<>(authService.login(req));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokensResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return new ApiResponse<>(authService.refresh(req.refreshToken()));
    }

    @PostMapping("/verify-otp")
    public ApiResponse<Void> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        authService.verifyOtp(req);
        return new ApiResponse<>(null);
    }
}
