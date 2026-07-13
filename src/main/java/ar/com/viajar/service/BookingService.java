package ar.com.viajar.service;

import ar.com.viajar.domain.Booking;
import ar.com.viajar.domain.Stop;
import ar.com.viajar.domain.Trip;
import ar.com.viajar.domain.TripSegment;
import ar.com.viajar.domain.User;
import ar.com.viajar.domain.Vehicle;
import ar.com.viajar.domain.enums.BookingStatus;
import ar.com.viajar.domain.enums.PaymentStatus;
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
import ar.com.viajar.service.pricing.RouteBuilder;
import ar.com.viajar.service.pricing.RoutePricing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;
    private final StopRepository stopRepository;
    private final TripSegmentRepository tripSegmentRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;

    public BookingResponse create(UUID passengerId, CreateBookingRequest req) {
        Trip trip = tripRepository.findById(req.tripId())
                .orElseThrow(() -> AppException.notFound("Viaje no encontrado"));
        if (trip.getStatus() != TripStatus.published) {
            throw AppException.conflict("El viaje no está disponible para reservar");
        }
        if (trip.getDriverId().equals(passengerId)) {
            throw AppException.badRequest("No podés reservar tu propio viaje");
        }

        List<Stop> stops = stopRepository.findAllByTripIdOrderByOrder(trip.getId());
        List<RouteBuilder.RoutePoint> route = RouteBuilder.build(trip, stops);
        int fromIndex = indexOfStop(route, req.fromStopId());
        if (fromIndex < 0) {
            throw AppException.badRequest("La parada de subida no pertenece a este viaje");
        }
        // Por ahora todo pasajero viaja hasta el destino final (no hay baja en paradas
        // intermedias todavía) — no tiene sentido subir en el último punto de la ruta.
        if (fromIndex == route.size() - 1) {
            throw AppException.badRequest("No hay ningún tramo para reservar desde ese punto");
        }

        List<TripSegment> segments = tripSegmentRepository.findAllByTripIdOrderByOrder(trip.getId());
        double totalPrice = RoutePricing.priceForRange(segments, route.size(), fromIndex, route.size() - 1);

        // Como todo pasajero llega hasta el destino final, dos reservas del mismo viaje siempre
        // se superponen (ambas terminan en el mismo punto) — alcanza con contar reservas activas
        // contra availableSeats, sin necesidad de chequear superposición por tramo.
        List<Booking> active = bookingRepository.findAllByTripIdAndStatus(trip.getId(), BookingStatus.confirmed);
        if (active.stream().anyMatch(b -> passengerId.equals(b.getPassengerId()))) {
            throw AppException.conflict("Ya tenés una reserva activa en este viaje");
        }
        if (active.size() >= trip.getAvailableSeats()) {
            throw AppException.conflict("SEAT_UNAVAILABLE");
        }
        Set<Integer> usedSeats = active.stream().map(Booking::getSeatNumber).collect(Collectors.toSet());
        int seatNumber = IntStream.rangeClosed(1, trip.getAvailableSeats())
                .filter(n -> !usedSeats.contains(n))
                .findFirst()
                .orElseThrow(() -> AppException.conflict("SEAT_UNAVAILABLE"));

        Booking booking = new Booking();
        booking.setPassengerId(passengerId);
        booking.setTripId(trip.getId());
        booking.setFromStopId(req.fromStopId());
        booking.setToStopId(null); // siempre destino final, por ahora
        booking.setSeatNumber(seatNumber);
        booking.setTotalPrice(totalPrice);
        booking.setPaymentStatus(PaymentStatus.pending); // sin Mercado Pago todavía (Fase 9)
        booking.setStatus(BookingStatus.confirmed);
        booking.setShareToken(UUID.randomUUID());
        booking = bookingRepository.save(booking);

        return toResponse(booking, trip);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getMine(UUID passengerId) {
        return bookingRepository.findAllByPassengerId(passengerId).stream()
                .map(b -> toResponse(b, tripRepository.findById(b.getTripId()).orElse(null)))
                .toList();
    }

    public BookingResponse cancel(UUID bookingId, UUID passengerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> AppException.notFound("Reserva no encontrada"));
        if (!booking.getPassengerId().equals(passengerId)) throw AppException.forbidden();
        if (booking.getStatus() != BookingStatus.confirmed) {
            throw AppException.conflict("La reserva ya no está activa");
        }
        booking.setStatus(BookingStatus.cancelled);
        // Reembolso: no aplica todavía (paymentStatus nunca pasó de "pending", no hay captura
        // real de Mercado Pago) — a definir cuando exista Fase 9.
        bookingRepository.save(booking);
        return toResponse(booking, tripRepository.findById(booking.getTripId()).orElse(null));
    }

    private int indexOfStop(List<RouteBuilder.RoutePoint> route, UUID stopId) {
        if (stopId == null) return 0;
        for (int i = 0; i < route.size(); i++) {
            if (stopId.equals(route.get(i).stopId())) return i;
        }
        return -1;
    }

    private BookingResponse toResponse(Booking booking, Trip trip) {
        String fromStopName;
        if (booking.getFromStopId() == null) {
            fromStopName = trip != null ? trip.getOriginName() : null;
        } else {
            fromStopName = stopRepository.findById(booking.getFromStopId()).map(Stop::getName).orElse(null);
        }

        User driver = trip != null ? userRepository.findById(trip.getDriverId()).orElse(null) : null;
        Vehicle vehicle = trip != null ? vehicleRepository.findById(trip.getVehicleId()).orElse(null) : null;

        return new BookingResponse(
                booking.getId(),
                booking.getTripId(),
                booking.getFromStopId(),
                fromStopName,
                booking.getSeatNumber(),
                booking.getTotalPrice(),
                booking.getPaymentStatus(),
                booking.getStatus(),
                booking.getCreatedAt(),
                trip != null ? trip.getOriginName() : null,
                trip != null ? trip.getDestinationName() : null,
                trip != null ? trip.getDepartureAt() : null,
                driver != null ? driver.getName() : null,
                vehicle != null ? vehicle.getBrand() : null,
                vehicle != null ? vehicle.getModel() : null
        );
    }
}
