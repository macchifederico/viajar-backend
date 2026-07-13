package ar.com.viajar.controller;

import ar.com.viajar.config.SecurityConfig;
import ar.com.viajar.domain.enums.BookingStatus;
import ar.com.viajar.domain.enums.PaymentStatus;
import ar.com.viajar.domain.enums.UserRole;
import ar.com.viajar.dto.response.BookingResponse;
import ar.com.viajar.exception.AppException;
import ar.com.viajar.security.JwtFilter;
import ar.com.viajar.security.JwtUtil;
import ar.com.viajar.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class BookingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean BookingService bookingService;
    @MockitoBean JwtUtil jwtUtil;

    private static final String TOKEN = "Bearer fake-token";

    private void mockAuthenticatedAs(UUID userId, UserRole role) {
        when(jwtUtil.extractUserIdFromAccess(anyString())).thenReturn(userId);
        when(jwtUtil.extractRoleFromAccess(anyString())).thenReturn(role);
    }

    private BookingResponse mockBooking(UUID id) {
        return new BookingResponse(
                id, UUID.randomUUID(), null, "Once", 1, 900.0,
                PaymentStatus.pending, BookingStatus.confirmed, Instant.now(),
                "Once", "La Plata", Instant.parse("2026-08-01T12:00:00Z"),
                "Juan Conductor", "Toyota", "Corolla"
        );
    }

    private String createBookingBody() {
        return """
                { "tripId": "%s" }
                """.formatted(UUID.randomUUID());
    }

    @Test
    void create_authenticated_returns201() throws Exception {
        UUID passengerId = UUID.randomUUID();
        mockAuthenticatedAs(passengerId, UserRole.passenger);
        when(bookingService.create(any(), any())).thenReturn(mockBooking(UUID.randomUUID()));

        mockMvc.perform(post("/bookings")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBookingBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("confirmed"));
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBookingBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_seatUnavailable_returns409() throws Exception {
        UUID passengerId = UUID.randomUUID();
        mockAuthenticatedAs(passengerId, UserRole.passenger);
        when(bookingService.create(any(), any())).thenThrow(AppException.conflict("SEAT_UNAVAILABLE"));

        mockMvc.perform(post("/bookings")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBookingBody()))
                .andExpect(status().isConflict());
    }

    @Test
    void getMine_authenticated_returns200() throws Exception {
        UUID passengerId = UUID.randomUUID();
        mockAuthenticatedAs(passengerId, UserRole.passenger);
        when(bookingService.getMine(passengerId)).thenReturn(List.of(mockBooking(UUID.randomUUID())));

        mockMvc.perform(get("/bookings/mine").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getMine_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/bookings/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void cancel_forbiddenWhenNotOwner_returns403() throws Exception {
        UUID passengerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        mockAuthenticatedAs(passengerId, UserRole.passenger);
        when(bookingService.cancel(bookingId, passengerId)).thenThrow(AppException.forbidden());

        mockMvc.perform(post("/bookings/{id}/cancel", bookingId).header("Authorization", TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_success_returns200() throws Exception {
        UUID passengerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        mockAuthenticatedAs(passengerId, UserRole.passenger);
        when(bookingService.cancel(bookingId, passengerId)).thenReturn(mockBooking(bookingId));

        mockMvc.perform(post("/bookings/{id}/cancel", bookingId).header("Authorization", TOKEN))
                .andExpect(status().isOk());
    }
}
