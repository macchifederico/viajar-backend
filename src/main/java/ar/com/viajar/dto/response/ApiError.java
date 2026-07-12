package ar.com.viajar.dto.response;

import java.util.List;

public record ApiError(String code, String message, List<FieldError> errors) {

    public ApiError(String code, String message) {
        this(code, message, null);
    }

    public record FieldError(String field, String message) {}
}
