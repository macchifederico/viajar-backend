package ar.com.viajar.service;

import ar.com.viajar.domain.User;
import ar.com.viajar.domain.enums.DriverStatus;
import ar.com.viajar.dto.request.DriverProfileRequest;
import ar.com.viajar.dto.response.UserResponse;
import ar.com.viajar.exception.AppException;
import ar.com.viajar.integration.S3Service;
import ar.com.viajar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
@RequiredArgsConstructor
public class DriverService {

    private static final int MIN_DRIVER_AGE = 21;

    private final UserRepository userRepository;
    private final S3Service s3Service;

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return UserResponse.from(findOrThrow(userId));
    }

    public UserResponse submitProfile(UUID userId, DriverProfileRequest req,
                                      MultipartFile dniPhoto, MultipartFile licensePhoto, MultipartFile criminalRecord) {
        LocalDate birth = LocalDate.parse(req.birthDate());
        if (Period.between(birth, LocalDate.now()).getYears() < MIN_DRIVER_AGE) {
            throw AppException.badRequest("El conductor debe tener al menos " + MIN_DRIVER_AGE + " años");
        }

        long ts = System.currentTimeMillis();
        String base = "drivers/" + userId + "/";

        String dniUrl, licUrl, crimUrl;
        try {
            var dni  = CompletableFuture.supplyAsync(() -> s3Service.upload(dniPhoto,       base + "dni-" + ts));
            var lic  = CompletableFuture.supplyAsync(() -> s3Service.upload(licensePhoto,   base + "license-" + ts));
            var crim = CompletableFuture.supplyAsync(() -> s3Service.upload(criminalRecord, base + "criminal-" + ts));
            CompletableFuture.allOf(dni, lic, crim).join();
            dniUrl  = dni.get();
            licUrl  = lic.get();
            crimUrl = crim.get();
        } catch (Exception e) {
            throw new RuntimeException("Error subiendo documentos", e);
        }

        User user = findOrThrow(userId);
        user.setDni(req.dni());
        user.setBirthDate(birth.atStartOfDay().toInstant(ZoneOffset.UTC));
        user.setLicenseCategory(req.licenseCategory());
        if (dniUrl != null)  user.setDniPhotoUrl(dniUrl);
        if (licUrl != null)  user.setLicensePhotoUrl(licUrl);
        if (crimUrl != null) user.setCriminalRecordUrl(crimUrl);
        user.setDriverStatus(DriverStatus.under_review);

        return UserResponse.from(userRepository.save(user));
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> AppException.notFound("Usuario no encontrado"));
    }
}
