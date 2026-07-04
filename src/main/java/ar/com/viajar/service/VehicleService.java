package ar.com.viajar.service;

import ar.com.viajar.domain.Vehicle;
import ar.com.viajar.dto.request.CreateVehicleRequest;
import ar.com.viajar.dto.response.VehicleResponse;
import ar.com.viajar.exception.AppException;
import ar.com.viajar.integration.S3Service;
import ar.com.viajar.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
@RequiredArgsConstructor
public class VehicleService {

    private static final int MAX_VEHICLE_AGE_YEARS = 15;

    private final VehicleRepository vehicleRepository;
    private final S3Service s3Service;

    @Transactional(readOnly = true)
    public List<VehicleResponse> getMyVehicles(UUID driverId) {
        return vehicleRepository.findAllByDriverId(driverId).stream()
                .map(VehicleResponse::from)
                .toList();
    }

    public VehicleResponse createVehicle(UUID driverId, CreateVehicleRequest req, Map<String, MultipartFile> files) {
        int minYear = java.time.Year.now().getValue() - MAX_VEHICLE_AGE_YEARS;
        if (req.year() < minYear) {
            throw AppException.badRequest("El vehículo no puede tener más de " + MAX_VEHICLE_AGE_YEARS + " años");
        }

        Map<String, String> urls = uploadVehicleFiles(driverId, files);

        Vehicle v = new Vehicle();
        v.setDriverId(driverId);
        v.setBrand(req.brand());
        v.setModel(req.model());
        v.setYear(req.year());
        v.setPlate(req.plate());
        v.setColor(req.color());
        v.setDoors(req.doors() != null ? req.doors() : 4);
        v.setHasAc(req.hasAc() != null && req.hasAc());
        v.setHasSeatbelts(req.hasSeatbelts() == null || req.hasSeatbelts());
        if (req.insurancePolicy() != null) v.setInsurancePolicy(req.insurancePolicy());
        if (req.insuranceExpiresAt() != null) v.setInsuranceExpiresAt(Instant.parse(req.insuranceExpiresAt() + "T00:00:00Z"));
        if (req.vtvExpiresAt() != null) v.setVtvExpiresAt(Instant.parse(req.vtvExpiresAt() + "T00:00:00Z"));
        if (urls.get("photo") != null) v.setPhotoUrl(urls.get("photo"));
        if (urls.get("cedula") != null) v.setCedulaUrl(urls.get("cedula"));
        if (urls.get("insurance") != null) v.setInsuranceUrl(urls.get("insurance"));
        if (urls.get("vtv") != null) v.setVtvUrl(urls.get("vtv"));

        return VehicleResponse.from(vehicleRepository.save(v));
    }

    public VehicleResponse updateVehicle(UUID vehicleId, UUID ownerId, CreateVehicleRequest req, Map<String, MultipartFile> files) {
        Vehicle v = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> AppException.notFound("Vehículo no encontrado"));

        if (!v.getDriverId().equals(ownerId)) throw AppException.forbidden();

        if (req.brand() != null) v.setBrand(req.brand());
        if (req.model() != null) v.setModel(req.model());
        if (req.year() != null) {
            int minYear = java.time.Year.now().getValue() - MAX_VEHICLE_AGE_YEARS;
            if (req.year() < minYear) throw AppException.badRequest("Año de vehículo fuera de rango");
            v.setYear(req.year());
        }
        if (req.color() != null) v.setColor(req.color());
        if (req.doors() != null) v.setDoors(req.doors());
        if (req.hasAc() != null) v.setHasAc(req.hasAc());
        if (req.hasSeatbelts() != null) v.setHasSeatbelts(req.hasSeatbelts());
        if (req.insurancePolicy() != null) v.setInsurancePolicy(req.insurancePolicy());
        if (req.insuranceExpiresAt() != null) v.setInsuranceExpiresAt(Instant.parse(req.insuranceExpiresAt() + "T00:00:00Z"));
        if (req.vtvExpiresAt() != null) v.setVtvExpiresAt(Instant.parse(req.vtvExpiresAt() + "T00:00:00Z"));

        Map<String, String> urls = uploadVehicleFiles(ownerId, files);
        if (urls.get("photo") != null) v.setPhotoUrl(urls.get("photo"));
        if (urls.get("cedula") != null) v.setCedulaUrl(urls.get("cedula"));
        if (urls.get("insurance") != null) v.setInsuranceUrl(urls.get("insurance"));
        if (urls.get("vtv") != null) v.setVtvUrl(urls.get("vtv"));

        return VehicleResponse.from(vehicleRepository.save(v));
    }

    private Map<String, String> uploadVehicleFiles(UUID driverId, Map<String, MultipartFile> files) {
        long ts = System.currentTimeMillis();
        String base = "vehicles/" + driverId + "/";
        try {
            var photo    = CompletableFuture.supplyAsync(() -> s3Service.upload(files.get("photo"),    base + "photo-" + ts));
            var cedula   = CompletableFuture.supplyAsync(() -> s3Service.upload(files.get("cedula"),   base + "cedula-" + ts));
            var insurance = CompletableFuture.supplyAsync(() -> s3Service.upload(files.get("insurance"), base + "insurance-" + ts));
            var vtv      = CompletableFuture.supplyAsync(() -> s3Service.upload(files.get("vtv"),      base + "vtv-" + ts));
            CompletableFuture.allOf(photo, cedula, insurance, vtv).join();
            return Map.of(
                    "photo",     photo.get() != null ? photo.get() : "",
                    "cedula",    cedula.get() != null ? cedula.get() : "",
                    "insurance", insurance.get() != null ? insurance.get() : "",
                    "vtv",       vtv.get() != null ? vtv.get() : ""
            );
        } catch (Exception e) {
            throw new RuntimeException("Error subiendo archivos", e);
        }
    }
}
