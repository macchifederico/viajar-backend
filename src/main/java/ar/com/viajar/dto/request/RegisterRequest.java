package ar.com.viajar.dto.request;

import ar.com.viajar.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String name,
        @Email @NotBlank String email,
        @NotBlank String phone,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "(?=.*[A-Za-z])(?=.*\\d).+", message = "La contraseña debe contener al menos una letra y un número")
        String password,
        @NotNull UserRole role
) {}
