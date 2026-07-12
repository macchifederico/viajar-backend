package ar.com.viajar.service.pricing;

import org.springframework.stereotype.Component;

/**
 * Placeholder temporal: no hay definición geográfica real de zonas
 * (conurbano/interurbano/CABA) todavía (ver "a definir" en docs/backend-spec.md).
 * Siempre devuelve conurbano (factor 1.0) hasta que se defina el mecanismo real
 * (polígonos, reverse-geocoding, bounding boxes).
 */
@Component
public class StubZonaResolver implements ZonaResolver {

    @Override
    public Zona resolve(double lat, double lng) {
        return Zona.conurbano;
    }
}
