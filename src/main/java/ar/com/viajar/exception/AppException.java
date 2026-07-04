package ar.com.viajar.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final int status;
    private final String code;

    public AppException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", message);
    }

    public static AppException unauthorized() {
        return new AppException(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", "No autorizado");
    }

    public static AppException unauthorized(String message) {
        return new AppException(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", message);
    }

    public static AppException forbidden() {
        return new AppException(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "Acceso denegado");
    }

    public static AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", message);
    }

    public static AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT.value(), "CONFLICT", message);
    }
}
