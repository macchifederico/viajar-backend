package ar.com.viajar.service;

import ar.com.viajar.domain.Stop;
import ar.com.viajar.domain.Trip;
import ar.com.viajar.domain.TripSegment;
import ar.com.viajar.domain.Vehicle;
import ar.com.viajar.domain.enums.TripStatus;
import ar.com.viajar.dto.request.AdjustSegmentPricesRequest;
import ar.com.viajar.dto.request.CreateTripRequest;
import ar.com.viajar.dto.request.StopRequest;
import ar.com.viajar.dto.request.TripSegmentPriceAdjustment;
import ar.com.viajar.dto.response.StopResponse;
import ar.com.viajar.dto.response.TripResponse;
import ar.com.viajar.dto.response.TripSegmentResponse;
import ar.com.viajar.exception.AppException;
import ar.com.viajar.repository.StopRepository;
import ar.com.viajar.repository.TripRepository;
import ar.com.viajar.repository.TripSegmentRepository;
import ar.com.viajar.repository.VehicleRepository;
import ar.com.viajar.service.pricing.GeoUtils;
import ar.com.viajar.service.pricing.ZonaResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class TripService {

    /** Descuento aplicado al tramo "recorrido completo" frente a la suma de los tramos individuales. */
    private static final double FULL_TRIP_DISCOUNT = 0.85;

    /** Debajo de esta distancia, origen y destino se consideran el mismo punto. */
    private static final double MIN_ROUTE_DISTANCE_KM = 0.05;

    private final TripRepository tripRepository;
    private final StopRepository stopRepository;
    private final TripSegmentRepository tripSegmentRepository;
    private final VehicleRepository vehicleRepository;
    private final ZonaResolver zonaResolver;

    @Value("${price.per-km-ars}")
    private double pricePerKmArs;

    public TripResponse create(UUID driverId, CreateTripRequest req) {
        Vehicle vehicle = validateVehicle(req.vehicleId(), driverId, req.availableSeats());
        validateRoute(req);

        Trip trip = new Trip();
        trip.setDriverId(driverId);
        applyTripFields(trip, req, vehicle);
        trip = tripRepository.save(trip);

        generateStopsAndSegments(trip, req);

        return getById(trip.getId(), driverId);
    }

    public TripResponse update(UUID tripId, UUID driverId, CreateTripRequest req) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> AppException.notFound("Viaje no encontrado"));
        if (!trip.getDriverId().equals(driverId)) throw AppException.forbidden();
        if (trip.getStatus() != TripStatus.draft) {
            throw AppException.conflict("Solo se puede editar un viaje en estado draft");
        }

        Vehicle vehicle = validateVehicle(req.vehicleId(), driverId, req.availableSeats());
        validateRoute(req);
        applyTripFields(trip, req, vehicle);
        trip = tripRepository.save(trip);

        // Editar ruta/paradas regenera todos los segments desde cero, así que cualquier ajuste
        // manual de finalPrice hecho vía PATCH /trips/:id/segments se pierde (vuelve a finalPrice
        // = suggestedPrice). Es el comportamiento esperado: los tramos cambian, el ajuste anterior
        // ya no corresponde a la nueva ruta.
        tripSegmentRepository.deleteAll(tripSegmentRepository.findAllByTripIdOrderByOrder(tripId));
        stopRepository.deleteAll(stopRepository.findAllByTripIdOrderByOrder(tripId));

        generateStopsAndSegments(trip, req);

        return getById(trip.getId(), driverId);
    }

    public TripResponse publish(UUID tripId, UUID driverId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> AppException.notFound("Viaje no encontrado"));
        if (!trip.getDriverId().equals(driverId)) throw AppException.forbidden();
        if (trip.getStatus() != TripStatus.draft) {
            throw AppException.conflict("El viaje ya fue publicado o no está en borrador");
        }
        if (trip.getAvailableSeats() < 1) {
            throw AppException.badRequest("Debe haber al menos 1 asiento disponible para publicar");
        }
        trip.setStatus(TripStatus.published);
        tripRepository.save(trip);
        return getById(tripId, driverId);
    }

    public TripResponse adjustSegmentPrices(UUID tripId, UUID driverId, AdjustSegmentPricesRequest req) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> AppException.notFound("Viaje no encontrado"));
        if (!trip.getDriverId().equals(driverId)) throw AppException.forbidden();
        if (trip.getStatus() != TripStatus.draft) {
            throw AppException.conflict("Solo se puede ajustar el precio de un viaje en estado draft");
        }

        Map<UUID, TripSegment> segmentsById = tripSegmentRepository.findAllByTripIdOrderByOrder(tripId).stream()
                .collect(Collectors.toMap(TripSegment::getId, s -> s));

        List<TripSegment> toSave = new ArrayList<>();
        for (TripSegmentPriceAdjustment adjustment : req.segments()) {
            TripSegment segment = segmentsById.get(adjustment.segmentId());
            if (segment == null) {
                throw AppException.badRequest("El tramo " + adjustment.segmentId() + " no pertenece a este viaje");
            }
            // Por ahora el conductor puede poner el precio que quiera, sin límite de rango
            // sobre el suggestedPrice (el margen ±30% de producto.md no se aplica todavía).
            segment.setFinalPrice(adjustment.finalPrice());
            toSave.add(segment);
        }
        tripSegmentRepository.saveAll(toSave);

        return getById(tripId, driverId);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getMine(UUID driverId) {
        return tripRepository.findAllByDriverId(driverId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TripResponse getById(UUID tripId, UUID requesterId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> AppException.notFound("Viaje no encontrado"));
        boolean isOwner = requesterId != null && trip.getDriverId().equals(requesterId);
        // Un trip en draft solo lo puede ver su dueño; para cualquier otro caso (incluido público
        // sin autenticar) se responde 404 en vez de 403 para no filtrar la existencia del viaje.
        if (!isOwner && trip.getStatus() == TripStatus.draft) {
            throw AppException.notFound("Viaje no encontrado");
        }
        return toResponse(trip);
    }

    public TripResponse cancel(UUID tripId, UUID driverId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> AppException.notFound("Viaje no encontrado"));
        if (!trip.getDriverId().equals(driverId)) throw AppException.forbidden();
        if (trip.getStatus() == TripStatus.completed || trip.getStatus() == TripStatus.cancelled) {
            throw AppException.conflict("El viaje ya está en un estado terminal");
        }
        trip.setStatus(TripStatus.cancelled);
        // TODO Fase 7: si hay bookings con paymentStatus == held, disparar reembolso según
        // anticipación a departureAt (política para cancelación con <2hs todavía a definir).
        tripRepository.save(trip);
        return toResponse(trip);
    }

    private Vehicle validateVehicle(UUID vehicleId, UUID driverId, int availableSeats) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> AppException.notFound("Vehículo no encontrado"));
        if (!vehicle.getDriverId().equals(driverId)) throw AppException.forbidden();
        if (availableSeats > vehicle.getSeats()) {
            throw AppException.badRequest("availableSeats supera la capacidad del vehículo");
        }
        return vehicle;
    }

    private void validateRoute(CreateTripRequest req) {
        double distanceKm = GeoUtils.haversineKm(
                req.originLat(), req.originLng(), req.destinationLat(), req.destinationLng());
        if (distanceKm < MIN_ROUTE_DISTANCE_KM) {
            throw AppException.badRequest("El origen y destino no pueden ser el mismo");
        }
    }

    private void applyTripFields(Trip trip, CreateTripRequest req, Vehicle vehicle) {
        trip.setVehicleId(vehicle.getId());
        trip.setOriginName(req.originName());
        trip.setOriginLat(req.originLat());
        trip.setOriginLng(req.originLng());
        trip.setDestinationName(req.destinationName());
        trip.setDestinationLat(req.destinationLat());
        trip.setDestinationLng(req.destinationLng());
        trip.setDepartureAt(req.departureAt());
        trip.setTotalSeats(vehicle.getSeats());
        trip.setAvailableSeats(req.availableSeats());
    }

    private List<StopRequest> sortedStops(List<StopRequest> stops) {
        List<StopRequest> list = stops != null ? stops : List.of();
        List<StopRequest> sorted = list.stream()
                .sorted(Comparator.comparingInt(StopRequest::order))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).order().equals(sorted.get(i - 1).order())) {
                throw AppException.badRequest("Paradas con order duplicado o fuera de orden");
            }
        }
        return sorted;
    }

    private record RoutePoint(UUID stopId, double lat, double lng) {}

    private void generateStopsAndSegments(Trip trip, CreateTripRequest req) {
        List<StopRequest> sorted = sortedStops(req.stops());

        List<RoutePoint> route = new ArrayList<>();
        route.add(new RoutePoint(null, req.originLat(), req.originLng()));
        for (StopRequest sr : sorted) {
            Stop stop = new Stop();
            stop.setTripId(trip.getId());
            stop.setName(sr.name());
            stop.setLat(sr.lat());
            stop.setLng(sr.lng());
            stop.setOrder(sr.order());
            stop = stopRepository.save(stop);
            route.add(new RoutePoint(stop.getId(), sr.lat(), sr.lng()));
        }
        route.add(new RoutePoint(null, req.destinationLat(), req.destinationLng()));

        double totalDistance = 0;
        double totalSuggested = 0;
        int order = 0;
        List<TripSegment> segments = new ArrayList<>();
        for (int i = 0; i < route.size() - 1; i++) {
            RoutePoint a = route.get(i);
            RoutePoint b = route.get(i + 1);
            double distanceKm = GeoUtils.haversineKm(a.lat(), a.lng(), b.lat(), b.lng());
            double factor = zonaResolver.resolve(a.lat(), a.lng()).factor();
            double suggestedPrice = distanceKm * pricePerKmArs * factor;

            TripSegment segment = new TripSegment();
            segment.setTripId(trip.getId());
            segment.setFromStopId(a.stopId());
            segment.setToStopId(b.stopId());
            segment.setDistanceKm(distanceKm);
            segment.setSuggestedPrice(suggestedPrice);
            segment.setFinalPrice(suggestedPrice);
            segment.setOrder(order++);
            segments.add(segment);

            totalDistance += distanceKm;
            totalSuggested += suggestedPrice;
        }

        // Con paradas intermedias, el recorrido completo (origen->destino) es un tramo aparte,
        // más barato que la suma de los tramos individuales (ver docs/backend-spec.md).
        if (route.size() > 2) {
            double rawFull = totalSuggested * FULL_TRIP_DISCOUNT;
            double fullPrice = Math.min(rawFull, totalSuggested * 0.95);

            TripSegment full = new TripSegment();
            full.setTripId(trip.getId());
            full.setFromStopId(null);
            full.setToStopId(null);
            full.setDistanceKm(totalDistance);
            full.setSuggestedPrice(fullPrice);
            full.setFinalPrice(fullPrice);
            full.setOrder(order);
            segments.add(full);
        }

        tripSegmentRepository.saveAll(segments);
    }

    private TripResponse toResponse(Trip trip) {
        List<StopResponse> stops = stopRepository.findAllByTripIdOrderByOrder(trip.getId()).stream()
                .map(StopResponse::from)
                .toList();
        List<TripSegmentResponse> segments = tripSegmentRepository.findAllByTripIdOrderByOrder(trip.getId()).stream()
                .map(TripSegmentResponse::from)
                .toList();
        return TripResponse.from(trip, stops, segments);
    }
}
