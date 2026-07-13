package ar.com.viajar.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TripSearchResult(
        UUID tripId,
        String driverName,
        double driverRatingAvg,
        String vehicleBrand,
        String vehicleModel,
        String originName,
        String destinationName,
        Instant departureAt,
        String matchedToName,
        UUID toStopId,
        double minPrice,
        int availableSeats
) {
}
