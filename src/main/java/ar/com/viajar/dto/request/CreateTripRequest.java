package ar.com.viajar.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateTripRequest(
        @NotNull UUID vehicleId,
        @NotBlank String originName,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double originLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double originLng,
        @NotBlank String destinationName,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double destinationLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double destinationLng,
        @NotNull @Future Instant departureAt,
        @NotNull @Min(1) Integer availableSeats,
        @Valid List<StopRequest> stops
) {}
