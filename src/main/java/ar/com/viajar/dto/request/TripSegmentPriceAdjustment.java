package ar.com.viajar.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record TripSegmentPriceAdjustment(
        @NotNull UUID segmentId,
        @NotNull @Positive Double finalPrice
) {}
