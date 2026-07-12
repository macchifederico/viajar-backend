package ar.com.viajar.controller;

import ar.com.viajar.domain.StoredImage;
import ar.com.viajar.exception.AppException;
import ar.com.viajar.repository.StoredImageRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

@RestController
@RequiredArgsConstructor
public class FilesController {

    private static final String PREFIX = "/files/";

    private final StoredImageRepository storedImageRepository;

    @GetMapping("/files/**")
    public ResponseEntity<byte[]> get(HttpServletRequest request) {
        String key = request.getRequestURI().substring(PREFIX.length());
        StoredImage image = storedImageRepository.findByKey(key)
                .orElseThrow(() -> AppException.notFound("Imagen no encontrada"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(Base64.getDecoder().decode(image.getData()));
    }
}
