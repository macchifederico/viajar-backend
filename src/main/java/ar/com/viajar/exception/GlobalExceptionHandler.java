package ar.com.viajar.exception;

import ar.com.viajar.dto.response.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Map<String, String> DUPLICATE_MESSAGES = Map.of(
            "users_email_key", "Ya existe una cuenta registrada con ese email",
            "users_phone_key", "Ya existe una cuenta registrada con ese teléfono",
            "users_dni_key", "Ya existe una cuenta registrada con ese DNI",
            "vehicles_plate_key", "Ya existe un vehículo registrado con esa patente"
    );

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleApp(AppException e) {
        return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        List<ApiError.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(err -> new ApiError.FieldError(err.getField(), err.getDefaultMessage()))
                .toList();
        String message = errors.isEmpty() ? "Datos inválidos" : errors.get(0).field() + ": " + errors.get(0).message();
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", message, errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "Body de request inválido o mal formado"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", e.getRequestPartName() + " es requerido"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("FORBIDDEN", "Acceso denegado"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException e) {
        String constraintName = null;
        Throwable cause = e.getCause();
        if (cause instanceof ConstraintViolationException cve) {
            constraintName = cve.getConstraintName();
        }
        String message = constraintName != null
                ? DUPLICATE_MESSAGES.getOrDefault(constraintName, "Registro duplicado")
                : "Registro duplicado";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONFLICT", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "Error interno del servidor"));
    }
}
