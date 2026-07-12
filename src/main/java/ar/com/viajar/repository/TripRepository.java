package ar.com.viajar.repository;

import ar.com.viajar.domain.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findAllByDriverId(UUID driverId);
}
