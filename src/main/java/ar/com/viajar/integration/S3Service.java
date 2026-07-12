package ar.com.viajar.integration;

import ar.com.viajar.domain.StoredImage;
import ar.com.viajar.repository.StoredImageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Base64;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final StoredImageRepository storedImageRepository;
    private final String bucket;
    private final String environment;
    private final String serverPort;

    public S3Service(
            S3Client s3Client,
            StoredImageRepository storedImageRepository,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${environment:}") String environment,
            @Value("${server.port}") String serverPort
    ) {
        this.s3Client = s3Client;
        this.storedImageRepository = storedImageRepository;
        this.bucket = bucket;
        this.environment = environment;
        this.serverPort = serverPort;
    }

    public String upload(MultipartFile file, String key) {
        if (file == null || file.isEmpty()) return null;

        if ("local".equals(environment)) {
            return uploadLocal(file, key);
        }

        if (bucket.isBlank()) {
            return "https://s3.example.com/" + key;
        }
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );
            return "https://" + bucket + ".s3.amazonaws.com/" + key;
        } catch (IOException e) {
            throw new RuntimeException("Error subiendo archivo a S3", e);
        }
    }

    private String uploadLocal(MultipartFile file, String key) {
        try {
            StoredImage image = storedImageRepository.findByKey(key).orElseGet(StoredImage::new);
            image.setKey(key);
            image.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
            image.setData(Base64.getEncoder().encodeToString(file.getBytes()));
            storedImageRepository.save(image);
            return "http://localhost:" + serverPort + "/files/" + key;
        } catch (IOException e) {
            throw new RuntimeException("Error guardando archivo local", e);
        }
    }
}
