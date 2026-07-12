package ar.com.viajar.repository;

import ar.com.viajar.domain.StoredImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoredImageRepository extends JpaRepository<StoredImage, UUID> {
    Optional<StoredImage> findByKey(String key);
}
