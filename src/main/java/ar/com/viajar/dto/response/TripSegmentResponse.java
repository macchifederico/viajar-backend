package ar.com.viajar.dto.response;

import ar.com.viajar.domain.TripSegment;

import java.util.UUID;

public record TripSegmentResponse(
        UUID id,
        UUID fromStopId,
        UUID toStopId,
        double distanceKm,
        double suggestedPrice,
        // Precio por pasajero/asiento reservado en este tramo, no un total del viaje completo.
        double finalPrice,
        int order
) {
    public static TripSegmentResponse from(TripSegment s) {
        return new TripSegmentResponse(
                s.getId(), s.getFromStopId(), s.getToStopId(),
                s.getDistanceKm(), s.getSuggestedPrice(), s.getFinalPrice(), s.getOrder()
        );
    }
}
