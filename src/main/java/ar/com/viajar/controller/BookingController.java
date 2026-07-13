package ar.com.viajar.controller;

import ar.com.viajar.dto.request.CreateBookingRequest;
import ar.com.viajar.dto.response.ApiResponse;
import ar.com.viajar.dto.response.BookingResponse;
import ar.com.viajar.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookingResponse> create(@AuthenticationPrincipal UUID passengerId, @Valid @RequestBody CreateBookingRequest req) {
        return new ApiResponse<>(bookingService.create(passengerId, req));
    }

    @GetMapping("/mine")
    public ApiResponse<List<BookingResponse>> getMine(@AuthenticationPrincipal UUID passengerId) {
        return new ApiResponse<>(bookingService.getMine(passengerId));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<BookingResponse> cancel(@PathVariable UUID id, @AuthenticationPrincipal UUID passengerId) {
        return new ApiResponse<>(bookingService.cancel(id, passengerId));
    }
}
