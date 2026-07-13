package ar.com.viajar.service.pricing;

import ar.com.viajar.domain.TripSegment;

import java.util.List;

/**
 * Precio de un rango de la ruta [fromIndex, toIndex] (índices de RouteBuilder.RoutePoint).
 * Si el rango cubre el recorrido completo (origen -> destino), usa el segment "completo" con
 * descuento en vez de sumar los tramos individuales (ver TripService.generateStopsAndSegments).
 */
public final class RoutePricing {

    private RoutePricing() {
    }

    public static double priceForRange(List<TripSegment> segments, int routeSize, int fromIndex, int toIndex) {
        if (fromIndex == 0 && toIndex == routeSize - 1) {
            TripSegment fullSegment = segments.stream()
                    .filter(s -> s.getFromStopId() == null && s.getToStopId() == null)
                    .findFirst()
                    .orElse(null);
            return fullSegment != null
                    ? fullSegment.getFinalPrice()
                    : segments.stream().mapToDouble(TripSegment::getFinalPrice).sum();
        }
        // El índice de ruta coincide con el `order` de los tramos individuales (ver
        // generateStopsAndSegments): el tramo `order == i` cubre route[i] -> route[i+1].
        return segments.stream()
                .filter(s -> s.getOrder() >= fromIndex && s.getOrder() <= toIndex - 1)
                .mapToDouble(TripSegment::getFinalPrice)
                .sum();
    }
}
