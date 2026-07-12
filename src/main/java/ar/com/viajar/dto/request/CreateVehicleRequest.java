package ar.com.viajar.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateVehicleRequest(
        @NotBlank String brand,
        @NotBlank String model,
        @NotNull Integer year,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}\\d{3}$|^[A-Za-z]{2}\\d{3}[A-Za-z]{2}$", message = "La patente debe tener formato AAA111 o AA111AA")
        String plate,
        @NotBlank String color,
        @NotNull @Min(2) @Max(4) Integer doors,
        @NotNull @Min(1) @Max(8) Integer seats,
        Boolean hasAc,
        Boolean hasSeatbelts,
        String insurancePolicy,
        String insuranceExpiresAt,
        @NotBlank String vtvExpiresAt
) {}
