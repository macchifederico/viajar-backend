package ar.com.viajar.service;

import ar.com.viajar.domain.Trip;
import ar.com.viajar.domain.TripSegment;
import ar.com.viajar.domain.Vehicle;
import ar.com.viajar.domain.enums.TripStatus;
import ar.com.viajar.dto.request.AdjustSegmentPricesRequest;
import ar.com.viajar.dto.request.CreateTripRequest;
import ar.com.viajar.dto.request.StopRequest;
import ar.com.viajar.dto.request.TripSegmentPriceAdjustment;
import ar.com.viajar.exception.AppException;
import ar.com.viajar.repository.StopRepository;
import ar.com.viajar.repository.TripRepository;
import ar.com.viajar.repository.TripSegmentRepository;
import ar.com.viajar.repository.VehicleRepository;
import ar.com.viajar.service.pricing.Zona;
import ar.com.viajar.service.pricing.ZonaResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock TripRepository tripRepository;
    @Mock StopRepository stopRepository;
    @Mock TripSegmentRepository tripSegmentRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock ZonaResolver zonaResolver;

    TripService tripService;

    UUID driverId;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, stopRepository, tripSegmentRepository, vehicleRepository, zonaResolver);
        ReflectionTestUtils.setField(tripService, "pricePerKmArs", 150.0);
        driverId = UUID.randomUUID();

        lenient().when(zonaResolver.resolve(anyDouble(), anyDouble())).thenReturn(Zona.conurbano);

        AtomicReference<ar.com.viajar.domain.Trip> savedTrip = new AtomicReference<>();
        lenient().when(tripRepository.save(any())).thenAnswer(inv -> {
            var trip = (ar.com.viajar.domain.Trip) inv.getArgument(0);
            if (trip.getId() == null) trip.setId(UUID.randomUUID());
            savedTrip.set(trip);
            return trip;
        });
        lenient().when(tripRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(savedTrip.get()));

        lenient().when(stopRepository.save(any())).thenAnswer(inv -> {
            var stop = (ar.com.viajar.domain.Stop) inv.getArgument(0);
            if (stop.getId() == null) stop.setId(UUID.randomUUID());
            return stop;
        });
    }

    private Vehicle vehicle(UUID id, UUID ownerId, int seats) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setDriverId(ownerId);
        v.setSeats(seats);
        return v;
    }

    private CreateTripRequest request(UUID vehicleId, int availableSeats, List<StopRequest> stops) {
        return new CreateTripRequest(
                vehicleId,
                "Once", -34.6083, -58.3712,
                "La Plata", -34.9214, -57.9544,
                Instant.parse("2026-08-01T12:00:00Z"),
                availableSeats,
                stops
        );
    }

    @Test
    void availableSeatsExceedingVehicleCapacity_throwsBadRequest() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle(vehicleId, driverId, 3)));

        AppException ex = assertThrows(AppException.class,
                () -> tripService.create(driverId, request(vehicleId, 4, List.of())));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void vehicleOwnedByAnotherDriver_throwsForbidden() {
        UUID vehicleId = UUID.randomUUID();
        UUID otherDriverId = UUID.randomUUID();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle(vehicleId, otherDriverId, 4)));

        AppException ex = assertThrows(AppException.class,
                () -> tripService.create(driverId, request(vehicleId, 2, List.of())));

        assertEquals(403, ex.getStatus());
    }

    @Test
    void nonexistentVehicle_throwsNotFound() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> tripService.create(driverId, request(vehicleId, 2, List.of())));

        assertEquals(404, ex.getStatus());
    }

    @Test
    void duplicateStopOrder_throwsBadRequest() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle(vehicleId, driverId, 4)));
        List<StopRequest> stops = List.of(
                new StopRequest("Parada A", -34.7, -58.4, 1),
                new StopRequest("Parada B", -34.8, -58.2, 1)
        );

        AppException ex = assertThrows(AppException.class,
                () -> tripService.create(driverId, request(vehicleId, 2, stops)));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void noIntermediateStops_createsSingleSegmentWithoutFullDiscount() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle(vehicleId, driverId, 4)));

        tripService.create(driverId, request(vehicleId, 2, List.of()));

        ArgumentCaptor<List<TripSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(tripSegmentRepository).saveAll(captor.capture());
        List<TripSegment> segments = captor.getValue();

        assertEquals(1, segments.size());
        assertNull(segments.get(0).getFromStopId());
        assertNull(segments.get(0).getToStopId());
    }

    @Test
    void nIntermediateStops_createsNPlusOneSegmentsPlusFullDiscounted() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle(vehicleId, driverId, 4)));
        List<StopRequest> stops = List.of(
                new StopRequest("Parada A", -34.65, -58.38, 1),
                new StopRequest("Parada B", -34.75, -58.20, 2)
        );

        tripService.create(driverId, request(vehicleId, 2, stops));

        ArgumentCaptor<List<TripSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(tripSegmentRepository).saveAll(captor.capture());
        List<TripSegment> segments = captor.getValue();

        // 2 paradas intermedias -> 3 tramos consecutivos + 1 tramo "recorrido completo"
        assertEquals(4, segments.size());

        List<TripSegment> consecutive = segments.stream()
                .filter(s -> s.getFromStopId() != null || s.getToStopId() != null)
                .toList();
        List<TripSegment> full = segments.stream()
                .filter(s -> s.getFromStopId() == null && s.getToStopId() == null)
                .toList();

        assertEquals(3, consecutive.size());
        assertEquals(1, full.size());

        double sumConsecutive = consecutive.stream().mapToDouble(TripSegment::getSuggestedPrice).sum();
        assertTrue(full.get(0).getSuggestedPrice() < sumConsecutive,
                "El precio del recorrido completo debe ser menor que la suma de los tramos individuales");
    }

    @Test
    void originEqualsDestination_throwsBadRequest() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle(vehicleId, driverId, 4)));
        CreateTripRequest req = new CreateTripRequest(
                vehicleId, "Once", -34.6083, -58.3712, "Once", -34.6083, -58.3712,
                Instant.parse("2026-08-01T12:00:00Z"), 2, List.of());

        AppException ex = assertThrows(AppException.class, () -> tripService.create(driverId, req));

        assertEquals(400, ex.getStatus());
    }

    private Trip draftTrip(UUID id, UUID ownerId) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setDriverId(ownerId);
        trip.setStatus(TripStatus.draft);
        return trip;
    }

    private TripSegment segment(UUID id, double suggestedPrice) {
        TripSegment s = new TripSegment();
        s.setId(id);
        s.setSuggestedPrice(suggestedPrice);
        s.setFinalPrice(suggestedPrice);
        return s;
    }

    @Test
    void adjustSegmentPrices_updatesFinalPrice() {
        UUID tripId = UUID.randomUUID();
        UUID segmentId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(draftTrip(tripId, driverId)));
        when(tripSegmentRepository.findAllByTripIdOrderByOrder(tripId)).thenReturn(List.of(segment(segmentId, 1000.0)));

        tripService.adjustSegmentPrices(tripId, driverId,
                new AdjustSegmentPricesRequest(List.of(new TripSegmentPriceAdjustment(segmentId, 1250.0))));

        ArgumentCaptor<List<TripSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(tripSegmentRepository).saveAll(captor.capture());
        assertEquals(1250.0, captor.getValue().get(0).getFinalPrice());
    }

    @Test
    void adjustSegmentPrices_outsideOldThirtyPercentMargin_isAcceptedWithoutRangeLimit() {
        // No hay límite de rango por ahora: el conductor puede poner el precio que quiera.
        UUID tripId = UUID.randomUUID();
        UUID segmentId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(draftTrip(tripId, driverId)));
        when(tripSegmentRepository.findAllByTripIdOrderByOrder(tripId)).thenReturn(List.of(segment(segmentId, 1000.0)));

        tripService.adjustSegmentPrices(tripId, driverId,
                new AdjustSegmentPricesRequest(List.of(new TripSegmentPriceAdjustment(segmentId, 5000.0))));

        ArgumentCaptor<List<TripSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(tripSegmentRepository).saveAll(captor.capture());
        assertEquals(5000.0, captor.getValue().get(0).getFinalPrice());
    }

    @Test
    void adjustSegmentPrices_segmentNotInTrip_throwsBadRequest() {
        UUID tripId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(draftTrip(tripId, driverId)));
        when(tripSegmentRepository.findAllByTripIdOrderByOrder(tripId)).thenReturn(List.of());

        AppException ex = assertThrows(AppException.class, () -> tripService.adjustSegmentPrices(tripId, driverId,
                new AdjustSegmentPricesRequest(List.of(new TripSegmentPriceAdjustment(UUID.randomUUID(), 1000.0)))));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void adjustSegmentPrices_notOwner_throwsForbidden() {
        UUID tripId = UUID.randomUUID();
        UUID otherDriverId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(draftTrip(tripId, otherDriverId)));

        AppException ex = assertThrows(AppException.class, () -> tripService.adjustSegmentPrices(tripId, driverId,
                new AdjustSegmentPricesRequest(List.of(new TripSegmentPriceAdjustment(UUID.randomUUID(), 1000.0)))));

        assertEquals(403, ex.getStatus());
    }

    @Test
    void adjustSegmentPrices_tripNotDraft_throwsConflict() {
        UUID tripId = UUID.randomUUID();
        Trip trip = draftTrip(tripId, driverId);
        trip.setStatus(TripStatus.published);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        AppException ex = assertThrows(AppException.class, () -> tripService.adjustSegmentPrices(tripId, driverId,
                new AdjustSegmentPricesRequest(List.of(new TripSegmentPriceAdjustment(UUID.randomUUID(), 1000.0)))));

        assertEquals(409, ex.getStatus());
    }
}
