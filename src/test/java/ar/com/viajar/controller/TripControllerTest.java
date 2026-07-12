package ar.com.viajar.controller;

import ar.com.viajar.config.SecurityConfig;
import ar.com.viajar.domain.enums.TripStatus;
import ar.com.viajar.domain.enums.UserRole;
import ar.com.viajar.dto.response.TripResponse;
import ar.com.viajar.exception.AppException;
import ar.com.viajar.security.JwtFilter;
import ar.com.viajar.security.JwtUtil;
import ar.com.viajar.service.TripService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TripController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class TripControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean TripService tripService;
    @MockitoBean JwtUtil jwtUtil;

    private static final String TOKEN = "Bearer fake-token";

    private void mockAuthenticatedAs(UUID userId, UserRole role) {
        when(jwtUtil.extractUserIdFromAccess(anyString())).thenReturn(userId);
        when(jwtUtil.extractRoleFromAccess(anyString())).thenReturn(role);
    }

    private TripResponse mockTrip(UUID driverId) {
        return new TripResponse(
                UUID.randomUUID(), driverId, UUID.randomUUID(),
                "Once", -34.6083, -58.3712,
                "La Plata", -34.9214, -57.9544,
                Instant.parse("2026-08-01T12:00:00Z"), 4, 4,
                TripStatus.draft, Instant.now(), List.of(), List.of()
        );
    }

    private String createTripBody() {
        return """
                {
                  "vehicleId": "%s",
                  "originName": "Once", "originLat": -34.6083, "originLng": -58.3712,
                  "destinationName": "La Plata", "destinationLat": -34.9214, "destinationLng": -57.9544,
                  "departureAt": "2026-08-01T12:00:00Z",
                  "availableSeats": 2,
                  "stops": []
                }
                """.formatted(UUID.randomUUID());
    }

    @Test
    void create_withDriverRole_returns201() throws Exception {
        UUID driverId = UUID.randomUUID();
        mockAuthenticatedAs(driverId, UserRole.driver);
        when(tripService.create(any(), any())).thenReturn(mockTrip(driverId));

        mockMvc.perform(post("/trips")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTripBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("draft"));
    }

    @Test
    void create_withPassengerRole_returns403AndNeverCallsService() throws Exception {
        mockAuthenticatedAs(UUID.randomUUID(), UserRole.passenger);

        mockMvc.perform(post("/trips")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTripBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(tripService);
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTripBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_withoutToken_isPublicAndReturns200() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(tripService.getById(tripId, null)).thenReturn(mockTrip(UUID.randomUUID()));

        mockMvc.perform(get("/trips/{id}", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originName").value("Once"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(tripService.getById(tripId, null)).thenThrow(AppException.notFound("Viaje no encontrado"));

        mockMvc.perform(get("/trips/{id}", tripId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getMine_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/trips/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void getMine_authenticated_returns200() throws Exception {
        UUID driverId = UUID.randomUUID();
        mockAuthenticatedAs(driverId, UserRole.driver);
        when(tripService.getMine(driverId)).thenReturn(List.of(mockTrip(driverId)));

        mockMvc.perform(get("/trips/mine").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void publish_conflictWhenNotDraft_returns409() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        mockAuthenticatedAs(driverId, UserRole.driver);
        when(tripService.publish(tripId, driverId)).thenThrow(AppException.conflict("El viaje ya fue publicado"));

        mockMvc.perform(post("/trips/{id}/publish", tripId).header("Authorization", TOKEN))
                .andExpect(status().isConflict());
    }

    @Test
    void update_ownerInDraft_returns200() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        mockAuthenticatedAs(driverId, UserRole.driver);
        when(tripService.update(any(), any(), any())).thenReturn(mockTrip(driverId));

        mockMvc.perform(put("/trips/{id}", tripId)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTripBody()))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_forbiddenWhenNotOwner_returns403() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        mockAuthenticatedAs(driverId, UserRole.driver);
        when(tripService.cancel(tripId, driverId)).thenThrow(AppException.forbidden());

        mockMvc.perform(delete("/trips/{id}", tripId).header("Authorization", TOKEN))
                .andExpect(status().isForbidden());
    }
}
