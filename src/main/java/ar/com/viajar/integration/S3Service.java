package ar.com.viajar.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final String bucket;

    public S3Service(S3Client s3Client, @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public String upload(MultipartFile file, String key) {
        if (file == null || file.isEmpty()) return null;
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
}
