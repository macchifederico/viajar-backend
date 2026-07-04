package ar.com.viajar.dto.response;

import ar.com.viajar.domain.User;
import ar.com.viajar.domain.enums.DriverStatus;
import ar.com.viajar.domain.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        UserRole role,
        String avatarUrl,
        double ratingAvg,
        int ratingCount,
        Instant verifiedAt,
        Instant createdAt,
        DriverStatus driverStatus,
        String dni,
        Instant birthDate,
        String licenseNumber,
        String licenseCategory
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(), u.getName(), u.getEmail(), u.getPhone(),
                u.getRole(), u.getAvatarUrl(), u.getRatingAvg(), u.getRatingCount(),
                u.getVerifiedAt(), u.getCreatedAt(),
                u.getDriverStatus(), u.getDni(), u.getBirthDate(),
                u.getLicenseNumber(), u.getLicenseCategory()
        );
    }
}
