package ar.com.viajar.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID tripId,
        UUID fromStopId
) {
}
