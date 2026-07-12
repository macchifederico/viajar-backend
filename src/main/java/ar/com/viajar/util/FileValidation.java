package ar.com.viajar.util;

import ar.com.viajar.exception.AppException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

public final class FileValidation {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "application/pdf"
    );

    private FileValidation() {}

    public static void validate(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest(fieldName + " es requerido");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw AppException.badRequest(fieldName + " no puede superar los 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw AppException.badRequest(fieldName + " debe ser un archivo JPG, PNG o PDF");
        }
    }
}
