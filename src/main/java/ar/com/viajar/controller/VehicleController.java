package ar.com.viajar.controller;

import ar.com.viajar.dto.request.CreateVehicleRequest;
import ar.com.viajar.dto.response.ApiResponse;
import ar.com.viajar.dto.response.VehicleResponse;
import ar.com.viajar.service.VehicleService;
import ar.com.viajar.util.FileValidation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping("/mine")
    public ApiResponse<List<VehicleResponse>> getMyVehicles(@AuthenticationPrincipal UUID userId) {
        return new ApiResponse<>(vehicleService.getMyVehicles(userId));
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VehicleResponse> createVehicle(
            @AuthenticationPrincipal UUID userId,
            @Valid @ModelAttribute CreateVehicleRequest req,
            @RequestPart(required = false) MultipartFile photo,
            @RequestPart MultipartFile cedula,
            @RequestPart MultipartFile insurance,
            @RequestPart MultipartFile vtv
    ) {
        FileValidation.validate(cedula, "La cédula del vehículo");
        FileValidation.validate(insurance, "La póliza de seguro");
        FileValidation.validate(vtv, "La VTV/RTO");
        return new ApiResponse<>(vehicleService.createVehicle(userId, req, buildFileMap(photo, cedula, insurance, vtv)));
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ApiResponse<VehicleResponse> updateVehicle(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId,
            @Valid @ModelAttribute CreateVehicleRequest req,
            @RequestPart(required = false) MultipartFile photo,
            @RequestPart(required = false) MultipartFile cedula,
            @RequestPart(required = false) MultipartFile insurance,
            @RequestPart(required = false) MultipartFile vtv
    ) {
        return new ApiResponse<>(vehicleService.updateVehicle(id, userId, req, buildFileMap(photo, cedula, insurance, vtv)));
    }

    private Map<String, MultipartFile> buildFileMap(MultipartFile photo, MultipartFile cedula,
                                                     MultipartFile insurance, MultipartFile vtv) {
        Map<String, MultipartFile> files = new HashMap<>();
        if (photo != null) files.put("photo", photo);
        if (cedula != null) files.put("cedula", cedula);
        if (insurance != null) files.put("insurance", insurance);
        if (vtv != null) files.put("vtv", vtv);
        return files;
    }
}
