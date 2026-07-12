package ar.com.viajar.dto.response;

import ar.com.viajar.domain.Vehicle;

import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        UUID driverId,
        String brand,
        String model,
        int year,
        String plate,
        String color,
        String photoUrl,
        Instant verifiedAt,
        String cedulaUrl,
        String insurancePolicy,
        String insuranceUrl,
        Instant insuranceExpiresAt,
        String vtvUrl,
        Instant vtvExpiresAt,
        int doors,
        int seats,
        boolean hasAc,
        boolean hasSeatbelts
) {
    public static VehicleResponse from(Vehicle v) {
        return new VehicleResponse(
                v.getId(), v.getDriverId(), v.getBrand(), v.getModel(),
                v.getYear(), v.getPlate(), v.getColor(), v.getPhotoUrl(),
                v.getVerifiedAt(), v.getCedulaUrl(), v.getInsurancePolicy(),
                v.getInsuranceUrl(), v.getInsuranceExpiresAt(),
                v.getVtvUrl(), v.getVtvExpiresAt(),
                v.getDoors(), v.getSeats(), v.isHasAc(), v.isHasSeatbelts()
        );
    }
}
