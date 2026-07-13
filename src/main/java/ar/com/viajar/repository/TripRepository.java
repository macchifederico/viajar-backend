package ar.com.viajar.repository;

import ar.com.viajar.domain.Trip;
import ar.com.viajar.domain.enums.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findAllByDriverId(UUID driverId);

    List<Trip> findAllByStatusAndDepartureAtAfter(TripStatus status, Instant after);
}
