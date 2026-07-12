package ar.com.viajar.repository;

import ar.com.viajar.domain.TripSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripSegmentRepository extends JpaRepository<TripSegment, UUID> {
    List<TripSegment> findAllByTripIdOrderByOrder(UUID tripId);
}
