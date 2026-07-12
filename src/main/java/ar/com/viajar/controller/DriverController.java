package ar.com.viajar.controller;

import ar.com.viajar.dto.request.DriverProfileRequest;
import ar.com.viajar.dto.response.ApiResponse;
import ar.com.viajar.dto.response.UserResponse;
import ar.com.viajar.service.DriverService;
import ar.com.viajar.util.FileValidation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @GetMapping("/profile")
    public ApiResponse<UserResponse> getProfile(@AuthenticationPrincipal UUID userId) {
        return new ApiResponse<>(driverService.getProfile(userId));
    }

    @PostMapping(value = "/profile", consumes = "multipart/form-data")
    public ApiResponse<UserResponse> submitProfile(
            @AuthenticationPrincipal UUID userId,
            @Valid @ModelAttribute DriverProfileRequest req,
            @RequestPart MultipartFile dniPhoto,
            @RequestPart MultipartFile licensePhoto,
            @RequestPart MultipartFile criminalRecord
    ) {
        FileValidation.validate(dniPhoto, "La foto del DNI");
        FileValidation.validate(licensePhoto, "La foto de la licencia");
        FileValidation.validate(criminalRecord, "El certificado de antecedentes penales");
        return new ApiResponse<>(driverService.submitProfile(userId, req, dniPhoto, licensePhoto, criminalRecord));
    }
}
