package ar.com.viajar.dto.response;

import ar.com.viajar.domain.enums.BookingStatus;
import ar.com.viajar.domain.enums.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID tripId,
        UUID fromStopId,
        String fromStopName,
        int seatNumber,
        double totalPrice,
        PaymentStatus paymentStatus,
        BookingStatus status,
        Instant createdAt,
        String originName,
        String destinationName,
        Instant departureAt,
        String driverName,
        String vehicleBrand,
        String vehicleModel
) {
}
