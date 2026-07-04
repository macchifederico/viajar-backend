package ar.com.viajar.service;

import ar.com.viajar.domain.User;
import ar.com.viajar.dto.response.UserResponse;
import ar.com.viajar.exception.AppException;
import ar.com.viajar.integration.S3Service;
import ar.com.viajar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final S3Service s3Service;

    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        return UserResponse.from(findOrThrow(userId));
    }

    @Transactional(readOnly = true)
    public UserResponse getPublicProfile(UUID userId) {
        return UserResponse.from(findOrThrow(userId));
    }

    public UserResponse updateMe(UUID userId, String name, MultipartFile avatar) {
        User user = findOrThrow(userId);

        if (name != null && !name.isBlank()) user.setName(name);

        if (avatar != null && !avatar.isEmpty()) {
            String url = s3Service.upload(avatar, "avatars/" + userId + "-" + System.currentTimeMillis());
            user.setAvatarUrl(url);
        }

        return UserResponse.from(userRepository.save(user));
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> AppException.notFound("Usuario no encontrado"));
    }
}
