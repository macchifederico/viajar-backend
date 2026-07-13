package ar.com.viajar.dto.response;

import ar.com.viajar.domain.enums.BookingStatus;

import java.util.UUID;

public record BookingSummaryResponse(
        UUID id,
        String passengerName,
        String fromStopName,
        int seatNumber,
        double totalPrice,
        BookingStatus status
) {
}
