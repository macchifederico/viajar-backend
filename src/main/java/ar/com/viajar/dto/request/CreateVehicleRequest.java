package ar.com.viajar.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateVehicleRequest(
        @NotBlank String brand,
        @NotBlank String model,
        @NotNull Integer year,
        @NotBlank String plate,
        @NotBlank String color,
        Integer doors,
        Boolean hasAc,
        Boolean hasSeatbelts,
        String insurancePolicy,
        String insuranceExpiresAt,
        String vtvExpiresAt
) {}
