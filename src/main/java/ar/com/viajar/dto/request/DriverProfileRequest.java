package ar.com.viajar.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DriverProfileRequest(
        @NotBlank @Size(min = 7, max = 10) String dni,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String birthDate,
        @NotBlank String licenseNumber,
        @NotBlank @Pattern(regexp = "D[1-4]") String licenseCategory
) {}
