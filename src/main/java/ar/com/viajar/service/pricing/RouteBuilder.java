package ar.com.viajar.service.pricing;

import ar.com.viajar.domain.Stop;
import ar.com.viajar.domain.Trip;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Arma la ruta ordenada de un viaje como una lista de puntos [origen, stops..., destino],
 * el mismo orden que usa TripService al generar los trip_segments. Reusada tanto por la
 * búsqueda (TripService.matchTrip) como por la creación de reservas (BookingService).
 */
public final class RouteBuilder {

    public record RoutePoint(UUID stopId, String name, double lat, double lng) {}

    private RouteBuilder() {
    }

    public static List<RoutePoint> build(Trip trip, List<Stop> orderedStops) {
        List<RoutePoint> route = new ArrayList<>();
        route.add(new RoutePoint(null, trip.getOriginName(), trip.getOriginLat(), trip.getOriginLng()));
        for (Stop stop : orderedStops) {
            route.add(new RoutePoint(stop.getId(), stop.getName(), stop.getLat(), stop.getLng()));
        }
        route.add(new RoutePoint(null, trip.getDestinationName(), trip.getDestinationLat(), trip.getDestinationLng()));
        return route;
    }
}
