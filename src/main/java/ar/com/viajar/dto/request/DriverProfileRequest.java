package ar.com.viajar.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DriverProfileRequest(
        @NotBlank @Pattern(regexp = "\\d{7,8}", message = "El DNI debe tener 7 u 8 dígitos numéricos") String dni,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String birthDate,
        @NotBlank @Pattern(regexp = "D[1-4]") String licenseCategory
) {}
