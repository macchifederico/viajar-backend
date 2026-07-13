package ar.com.viajar.repository;

import ar.com.viajar.domain.Booking;
import ar.com.viajar.domain.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findAllByPassengerId(UUID passengerId);

    List<Booking> findAllByTripIdAndStatus(UUID tripId, BookingStatus status);
}
