package ar.com.viajar.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(@NotBlank String name) {}
