package ar.com.viajar.integration;

import ar.com.viajar.domain.User;
import ar.com.viajar.domain.Vehicle;
import ar.com.viajar.domain.enums.UserRole;
import ar.com.viajar.dto.request.CreateTripRequest;
import ar.com.viajar.dto.request.StopRequest;
import ar.com.viajar.dto.response.ApiResponse;
import ar.com.viajar.dto.response.TripResponse;
import ar.com.viajar.repository.UserRepository;
import ar.com.viajar.repository.VehicleRepository;
import ar.com.viajar.security.JwtUtil;
import ar.com.viajar.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private static final ParameterizedTypeReference<ApiResponse<TripResponse>> TRIP_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private User persistUser(UserRole role) {
        User user = new User();
        user.setName("Test " + role);
        user.setEmail(role + "-" + UUID.randomUUID() + "@test.com");
        user.setPhone("+5491" + Math.abs(UUID.randomUUID().hashCode() % 100000000));
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setRole(role);
        return userRepository.save(user);
    }

    private HttpHeaders authHeaders(UUID userId, UserRole role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generateAccessToken(userId, role));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void createPublishAndGetTrip_endToEnd() {
        User driver = persistUser(UserRole.driver);

        Vehicle vehicle = new Vehicle();
        vehicle.setDriverId(driver.getId());
        vehicle.setBrand("Chevrolet");
        vehicle.setModel("Onix");
        vehicle.setYear(2020);
        vehicle.setPlate("AB" + Math.abs(UUID.randomUUID().hashCode() % 1000) + "CD");
        vehicle.setColor("gris");
        vehicle.setSeats(4);
        vehicle = vehicleRepository.save(vehicle);

        CreateTripRequest req = new CreateTripRequest(
                vehicle.getId(),
                "Once", -34.6083, -58.3712,
                "La Plata", -34.9214, -57.9544,
                Instant.parse("2026-08-01T12:00:00Z"),
                3,
                List.of(new StopRequest("Quilmes", -34.72, -58.25, 1))
        );

        HttpHeaders driverHeaders = authHeaders(driver.getId(), UserRole.driver);
        ResponseEntity<ApiResponse<TripResponse>> createResp = restTemplate.exchange(
                "/trips", HttpMethod.POST, new HttpEntity<>(req, driverHeaders), TRIP_RESPONSE);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TripResponse created = createResp.getBody().data();
        assertThat(created.status().name()).isEqualTo("draft");
        // 1 parada intermedia -> 2 tramos consecutivos + 1 tramo "recorrido completo"
        assertThat(created.segments()).hasSize(3);

        UUID tripId = created.id();

        // en draft, sin token no se puede ver (404, no se filtra su existencia)
        ResponseEntity<String> draftPublicResp = restTemplate.getForEntity("/trips/{id}", String.class, tripId);
        assertThat(draftPublicResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // publicar
        ResponseEntity<ApiResponse<TripResponse>> publishResp = restTemplate.exchange(
                "/trips/{id}/publish", HttpMethod.POST, new HttpEntity<>(null, driverHeaders), TRIP_RESPONSE, tripId);
        assertThat(publishResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publishResp.getBody().data().status().name()).isEqualTo("published");

        // ahora sí es visible públicamente
        ResponseEntity<ApiResponse<TripResponse>> publicResp = restTemplate.exchange(
                "/trips/{id}", HttpMethod.GET, HttpEntity.EMPTY, TRIP_RESPONSE, tripId);
        assertThat(publicResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicResp.getBody().data().originName()).isEqualTo("Once");
    }

    @Test
    void passengerRoleCannotCreateTrip() {
        User passenger = persistUser(UserRole.passenger);

        CreateTripRequest req = new CreateTripRequest(
                UUID.randomUUID(), "Once", -34.6, -58.3, "La Plata", -34.9, -57.9,
                Instant.parse("2026-08-01T12:00:00Z"), 2, List.of());

        ResponseEntity<String> resp = restTemplate.exchange(
                "/trips", HttpMethod.POST,
                new HttpEntity<>(req, authHeaders(passenger.getId(), UserRole.passenger)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
