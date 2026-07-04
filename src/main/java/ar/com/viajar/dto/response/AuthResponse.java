package ar.com.viajar.dto.response;

public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {}
