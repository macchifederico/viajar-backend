package ar.com.viajar.repository;

import ar.com.viajar.domain.Stop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StopRepository extends JpaRepository<Stop, UUID> {
    List<Stop> findAllByTripIdOrderByOrder(UUID tripId);
}
