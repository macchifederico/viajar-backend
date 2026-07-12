package ar.com.viajar.dto.response;

import ar.com.viajar.domain.Trip;
import ar.com.viajar.domain.enums.TripStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TripResponse(
        UUID id,
        UUID driverId,
        UUID vehicleId,
        String originName,
        double originLat,
        double originLng,
        String destinationName,
        double destinationLat,
        double destinationLng,
        Instant departureAt,
        int totalSeats,
        int availableSeats,
        TripStatus status,
        Instant createdAt,
        List<StopResponse> stops,
        List<TripSegmentResponse> segments
) {
    public static TripResponse from(Trip t, List<StopResponse> stops, List<TripSegmentResponse> segments) {
        return new TripResponse(
                t.getId(), t.getDriverId(), t.getVehicleId(),
                t.getOriginName(), t.getOriginLat(), t.getOriginLng(),
                t.getDestinationName(), t.getDestinationLat(), t.getDestinationLng(),
                t.getDepartureAt(), t.getTotalSeats(), t.getAvailableSeats(),
                t.getStatus(), t.getCreatedAt(), stops, segments
        );
    }
}
