package ar.com.viajar.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AdjustSegmentPricesRequest(
        @NotEmpty @Valid List<TripSegmentPriceAdjustment> segments
) {}
