package ar.com.viajar.dto.response;

import ar.com.viajar.domain.Stop;

import java.time.Instant;
import java.util.UUID;

public record StopResponse(
        UUID id,
        String name,
        double lat,
        double lng,
        int order,
        Instant estimatedArrivalAt
) {
    public static StopResponse from(Stop s) {
        return new StopResponse(s.getId(), s.getName(), s.getLat(), s.getLng(), s.getOrder(), s.getEstimatedArrivalAt());
    }
}
