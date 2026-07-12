package ar.com.viajar.controller;

import ar.com.viajar.dto.request.AdjustSegmentPricesRequest;
import ar.com.viajar.dto.request.CreateTripRequest;
import ar.com.viajar.dto.response.ApiResponse;
import ar.com.viajar.dto.response.TripResponse;
import ar.com.viajar.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DRIVER','BOTH')")
    public ApiResponse<TripResponse> create(@AuthenticationPrincipal UUID driverId, @Valid @RequestBody CreateTripRequest req) {
        return new ApiResponse<>(tripService.create(driverId, req));
    }

    @PutMapping("/{id}")
    public ApiResponse<TripResponse> update(@PathVariable UUID id, @AuthenticationPrincipal UUID driverId,
                                            @Valid @RequestBody CreateTripRequest req) {
        return new ApiResponse<>(tripService.update(id, driverId, req));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<TripResponse> publish(@PathVariable UUID id, @AuthenticationPrincipal UUID driverId) {
        return new ApiResponse<>(tripService.publish(id, driverId));
    }

    @PatchMapping("/{id}/segments")
    @PreAuthorize("hasAnyRole('DRIVER','BOTH')")
    public ApiResponse<TripResponse> adjustSegmentPrices(@PathVariable UUID id, @AuthenticationPrincipal UUID driverId,
                                                         @Valid @RequestBody AdjustSegmentPricesRequest req) {
        return new ApiResponse<>(tripService.adjustSegmentPrices(id, driverId, req));
    }

    @GetMapping("/mine")
    public ApiResponse<List<TripResponse>> getMine(@AuthenticationPrincipal UUID driverId) {
        return new ApiResponse<>(tripService.getMine(driverId));
    }

    @GetMapping("/{id}")
    public ApiResponse<TripResponse> getById(@PathVariable UUID id, @AuthenticationPrincipal UUID requesterId) {
        return new ApiResponse<>(tripService.getById(id, requesterId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<TripResponse> cancel(@PathVariable UUID id, @AuthenticationPrincipal UUID driverId) {
        return new ApiResponse<>(tripService.cancel(id, driverId));
    }
}
