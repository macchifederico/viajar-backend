package ar.com.viajar.controller;

import ar.com.viajar.dto.response.ApiResponse;
import ar.com.viajar.dto.response.PlaceDetailsResponse;
import ar.com.viajar.dto.response.PlaceSuggestionResponse;
import ar.com.viajar.dto.response.RouteResponse;
import ar.com.viajar.integration.GooglePlacesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlacesController {

    private final GooglePlacesService googlePlacesService;

    @GetMapping("/autocomplete")
    public ApiResponse<List<PlaceSuggestionResponse>> autocomplete(
            @RequestParam String input,
            @RequestParam String sessionToken
    ) {
        return new ApiResponse<>(googlePlacesService.autocomplete(input, sessionToken));
    }

    @GetMapping("/route")
    public ApiResponse<RouteResponse> route(
            @RequestParam double originLat,
            @RequestParam double originLng,
            @RequestParam double destinationLat,
            @RequestParam double destinationLng,
            @RequestParam(required = false) String stops
    ) {
        String polyline = googlePlacesService.route(originLat, originLng, destinationLat, destinationLng, parseStops(stops));
        return new ApiResponse<>(new RouteResponse(polyline));
    }

    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetailsResponse> details(
            @PathVariable String placeId,
            @RequestParam String sessionToken
    ) {
        return new ApiResponse<>(googlePlacesService.details(placeId, sessionToken));
    }

    private List<double[]> parseStops(String stops) {
        if (stops == null || stops.isBlank()) return List.of();
        return Arrays.stream(stops.split(";"))
                .map(pair -> pair.split(","))
                .map(parts -> new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])})
                .toList();
    }
}
