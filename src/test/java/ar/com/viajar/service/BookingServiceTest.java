package ar.com.viajar.service;

import ar.com.viajar.domain.Booking;
import ar.com.viajar.domain.Stop;
import ar.com.viajar.domain.Trip;
import ar.com.viajar.domain.TripSegment;
import ar.com.viajar.domain.enums.BookingStatus;
import ar.com.viajar.domain.enums.TripStatus;
import ar.com.viajar.dto.request.CreateBookingRequest;
import ar.com.viajar.dto.response.BookingResponse;
import ar.com.viajar.exception.AppException;
import ar.com.viajar.repository.BookingRepository;
import ar.com.viajar.repository.StopRepository;
import ar.com.viajar.repository.TripRepository;
import ar.com.viajar.repository.TripSegmentRepository;
import ar.com.viajar.repository.UserRepository;
import ar.com.viajar.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock TripRepository tripRepository;
    @Mock StopRepository stopRepository;
    @Mock TripSegmentRepository tripSegmentRepository;
    @Mock UserRepository userRepository;
    @Mock VehicleRepository vehicleRepository;

    BookingService bookingService;

    UUID driverId;
    UUID passengerId;

    private static final double ORIGIN_LAT = -34.6083, ORIGIN_LNG = -58.3712;
    private static final double STOP_A_LAT = -34.65, STOP_A_LNG = -58.38;
    private static final double STOP_B_LAT = -34.75, STOP_B_LNG = -58.20;
    private static final double DEST_LAT = -34.9214, DEST_LNG = -57.9544;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, tripRepository, stopRepository,
                tripSegmentRepository, userRepository, vehicleRepository);
        driverId = UUID.randomUUID();
        passengerId = UUID.randomUUID();
    }

    private Stop stop(String name, double lat, double lng, int order) {
        Stop s = new Stop();
        s.setId(UUID.randomUUID());
        s.setName(name);
        s.setLat(lat);
        s.setLng(lng);
        s.setOrder(order);
        return s;
    }

    private TripSegment leg(int order, UUID fromStopId, UUID toStopId, double finalPrice) {
        TripSegment s = new TripSegment();
        s.setId(UUID.randomUUID());
        s.setFromStopId(fromStopId);
        s.setToStopId(toStopId);
        s.setFinalPrice(finalPrice);
        s.setOrder(order);
        return s;
    }

    /** Trip publicado Once -> ParadaA -> ParadaB -> La Plata, con availableSeats configurable. */
    private Trip publishedTripWithTwoStops(int availableSeats, Stop[] outStops) {
        UUID tripId = UUID.randomUUID();
        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setDriverId(driverId);
        trip.setOriginName("Once");
        trip.setOriginLat(ORIGIN_LAT);
        trip.setOriginLng(ORIGIN_LNG);
        trip.setDestinationName("La Plata");
        trip.setDestinationLat(DEST_LAT);
        trip.setDestinationLng(DEST_LNG);
        trip.setDepartureAt(Instant.now().plus(2, ChronoUnit.DAYS));
        trip.setAvailableSeats(availableSeats);
        trip.setStatus(TripStatus.published);

        Stop stopA = stop("Parada A", STOP_A_LAT, STOP_A_LNG, 1);
        Stop stopB = stop("Parada B", STOP_B_LAT, STOP_B_LNG, 2);
        outStops[0] = stopA;
        outStops[1] = stopB;
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        lenient().when(stopRepository.findAllByTripIdOrderByOrder(tripId)).thenReturn(List.of(stopA, stopB));
        lenient().when(stopRepository.findById(stopA.getId())).thenReturn(Optional.of(stopA));
        lenient().when(stopRepository.findById(stopB.getId())).thenReturn(Optional.of(stopB));

        List<TripSegment> segments = List.of(
                leg(0, null, stopA.getId(), 500.0),
                leg(1, stopA.getId(), stopB.getId(), 300.0),
                leg(2, stopB.getId(), null, 400.0),
                leg(3, null, null, 900.0)
        );
        lenient().when(tripSegmentRepository.findAllByTripIdOrderByOrder(tripId)).thenReturn(segments);
        lenient().when(bookingRepository.findAllByTripIdAndStatus(tripId, BookingStatus.confirmed)).thenReturn(List.of());
        lenient().when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        return trip;
    }

    @Test
    void create_fromOrigin_usesFullRouteDiscountedPrice() {
        Trip trip = publishedTripWithTwoStops(3, new Stop[2]);

        BookingResponse response = bookingService.create(passengerId, new CreateBookingRequest(trip.getId(), null));

        assertEquals(900.0, response.totalPrice());
        assertEquals(1, response.seatNumber());
        assertNull(response.fromStopId());
    }

    @Test
    void create_fromIntermediateStop_sumsRemainingLegs() {
        Stop[] stops = new Stop[2];
        Trip trip = publishedTripWithTwoStops(3, stops);

        BookingResponse response = bookingService.create(passengerId, new CreateBookingRequest(trip.getId(), stops[0].getId()));

        assertEquals(700.0, response.totalPrice()); // StopA->StopB (300) + StopB->Destino (400)
    }

    @Test
    void create_noSeatsAvailable_throwsConflict() {
        Trip trip = publishedTripWithTwoStops(1, new Stop[2]);
        Booking existing = new Booking();
        existing.setId(UUID.randomUUID());
        existing.setSeatNumber(1);
        existing.setStatus(BookingStatus.confirmed);
        when(bookingRepository.findAllByTripIdAndStatus(trip.getId(), BookingStatus.confirmed))
                .thenReturn(List.of(existing));

        CreateBookingRequest request = new CreateBookingRequest(trip.getId(), null);
        AppException ex = assertThrows(AppException.class, () -> bookingService.create(passengerId, request));

        assertEquals(409, ex.getStatus());
    }

    @Test
    void create_passengerAlreadyHasActiveBookingOnTrip_throwsConflict() {
        Trip trip = publishedTripWithTwoStops(3, new Stop[2]);
        Booking existing = new Booking();
        existing.setId(UUID.randomUUID());
        existing.setPassengerId(passengerId);
        existing.setSeatNumber(1);
        existing.setStatus(BookingStatus.confirmed);
        when(bookingRepository.findAllByTripIdAndStatus(trip.getId(), BookingStatus.confirmed))
                .thenReturn(List.of(existing));

        CreateBookingRequest request = new CreateBookingRequest(trip.getId(), null);
        AppException ex = assertThrows(AppException.class, () -> bookingService.create(passengerId, request));

        assertEquals(409, ex.getStatus());
    }

    @Test
    void create_tripNotPublished_throwsConflict() {
        Trip trip = publishedTripWithTwoStops(3, new Stop[2]);
        trip.setStatus(TripStatus.draft);

        CreateBookingRequest request = new CreateBookingRequest(trip.getId(), null);
        AppException ex = assertThrows(AppException.class, () -> bookingService.create(passengerId, request));

        assertEquals(409, ex.getStatus());
    }

    @Test
    void create_ownTrip_throwsBadRequest() {
        Trip trip = publishedTripWithTwoStops(3, new Stop[2]);

        CreateBookingRequest request = new CreateBookingRequest(trip.getId(), null);
        AppException ex = assertThrows(AppException.class, () -> bookingService.create(driverId, request));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void create_fromStopNotInTrip_throwsBadRequest() {
        Trip trip = publishedTripWithTwoStops(3, new Stop[2]);

        CreateBookingRequest request = new CreateBookingRequest(trip.getId(), UUID.randomUUID());
        AppException ex = assertThrows(AppException.class, () -> bookingService.create(passengerId, request));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void cancel_ownBooking_succeeds() {
        UUID bookingId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setPassengerId(passengerId);
        booking.setTripId(tripId);
        booking.setStatus(BookingStatus.confirmed);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        bookingService.cancel(bookingId, passengerId);

        assertEquals(BookingStatus.cancelled, booking.getStatus());
        verify(bookingRepository).save(booking);
    }

    @Test
    void cancel_notOwner_throwsForbidden() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setPassengerId(UUID.randomUUID());
        booking.setStatus(BookingStatus.confirmed);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        AppException ex = assertThrows(AppException.class, () -> bookingService.cancel(bookingId, passengerId));

        assertEquals(403, ex.getStatus());
    }

    @Test
    void cancel_alreadyCancelled_throwsConflict() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setPassengerId(passengerId);
        booking.setStatus(BookingStatus.cancelled);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        AppException ex = assertThrows(AppException.class, () -> bookingService.cancel(bookingId, passengerId));

        assertEquals(409, ex.getStatus());
    }
}
