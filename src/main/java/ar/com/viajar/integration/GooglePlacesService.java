package ar.com.viajar.integration;

import ar.com.viajar.dto.response.PlaceDetailsResponse;
import ar.com.viajar.dto.response.PlaceSuggestionResponse;
import ar.com.viajar.exception.AppException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
public class GooglePlacesService {

    private static final String AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete";
    private static final String DETAILS_URL_TEMPLATE = "https://places.googleapis.com/v1/places/{placeId}";
    private static final String ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";

    private final RestClient restClient = RestClient.create();
    private final String apiKey;

    public GooglePlacesService(@Value("${google.places.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public List<PlaceSuggestionResponse> autocomplete(String input, String sessionToken) {
        requireApiKey();
        try {
            AutocompleteApiResponse response = restClient.post()
                    .uri(AUTOCOMPLETE_URL)
                    .header("X-Goog-Api-Key", apiKey)
                    .body(Map.of(
                            "input", input,
                            "sessionToken", sessionToken,
                            "languageCode", "es",
                            "regionCode", "AR"
                    ))
                    .retrieve()
                    .body(AutocompleteApiResponse.class);

            if (response == null || response.suggestions() == null) {
                return List.of();
            }
            return response.suggestions().stream()
                    .map(RawSuggestion::placePrediction)
                    .filter(p -> p != null)
                    .map(p -> new PlaceSuggestionResponse(
                            p.placeId(),
                            p.text() != null ? p.text().text() : null,
                            p.structuredFormat() != null && p.structuredFormat().mainText() != null
                                    ? p.structuredFormat().mainText().text() : null,
                            p.structuredFormat() != null && p.structuredFormat().secondaryText() != null
                                    ? p.structuredFormat().secondaryText().text() : null
                    ))
                    .toList();
        } catch (RestClientException e) {
            throw AppException.badRequest("No se pudo obtener sugerencias de direcciones");
        }
    }

    public PlaceDetailsResponse details(String placeId, String sessionToken) {
        requireApiKey();
        try {
            RawPlaceDetails response = restClient.get()
                    .uri(DETAILS_URL_TEMPLATE + "?sessionToken={sessionToken}", placeId, sessionToken)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "id,displayName,formattedAddress,location")
                    .retrieve()
                    .body(RawPlaceDetails.class);

            if (response == null || response.location() == null) {
                throw AppException.notFound("Dirección no encontrada");
            }
            return new PlaceDetailsResponse(
                    response.id(),
                    response.displayName() != null ? response.displayName().text() : response.formattedAddress(),
                    response.formattedAddress(),
                    response.location().latitude(),
                    response.location().longitude()
            );
        } catch (RestClientException e) {
            throw AppException.badRequest("No se pudo obtener el detalle de la dirección");
        }
    }

    public String route(double originLat, double originLng, double destinationLat, double destinationLng,
                         List<double[]> stops) {
        requireApiKey();
        String polyline = fetchRoutePolyline(originLat, originLng, destinationLat, destinationLng, stops);
        if (polyline == null) {
            throw AppException.badRequest("No se pudo calcular la ruta");
        }
        return polyline;
    }

    private String fetchRoutePolyline(double originLat, double originLng, double destinationLat, double destinationLng,
                                       List<double[]> stops) {
        List<Map<String, Object>> intermediates = stops.stream()
                .map(s -> Map.<String, Object>of("location", Map.of("latLng", Map.of("latitude", s[0], "longitude", s[1]))))
                .toList();

        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "origin", Map.of("location", Map.of("latLng", Map.of("latitude", originLat, "longitude", originLng))),
                "destination", Map.of("location", Map.of("latLng", Map.of("latitude", destinationLat, "longitude", destinationLng))),
                "travelMode", "DRIVE"
        ));
        if (!intermediates.isEmpty()) {
            body.put("intermediates", intermediates);
        }

        try {
            RoutesApiResponse response = restClient.post()
                    .uri(ROUTES_URL)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "routes.polyline.encodedPolyline")
                    .body(body)
                    .retrieve()
                    .body(RoutesApiResponse.class);
            if (response == null || response.routes() == null || response.routes().isEmpty()) {
                return null;
            }
            RawEncodedPolyline polyline = response.routes().get(0).polyline();
            return polyline != null ? polyline.encodedPolyline() : null;
        } catch (RestClientException e) {
            return null;
        }
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw AppException.badRequest("El servicio de direcciones no está configurado (falta GOOGLE_MAPS_KEY)");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AutocompleteApiResponse(List<RawSuggestion> suggestions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawSuggestion(RawPlacePrediction placePrediction) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawPlacePrediction(String placeId, RawText text, RawStructuredFormat structuredFormat) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawStructuredFormat(RawText mainText, RawText secondaryText) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawText(String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawPlaceDetails(String id, RawText displayName, String formattedAddress, RawLocation location) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawLocation(double latitude, double longitude) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RoutesApiResponse(List<RawComputedRoute> routes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawComputedRoute(RawEncodedPolyline polyline) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawEncodedPolyline(String encodedPolyline) {}
}
