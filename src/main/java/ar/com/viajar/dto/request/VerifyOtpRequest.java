package ar.com.viajar.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank String phone,
        @NotBlank @Size(min = 6, max = 6) String code
) {}
