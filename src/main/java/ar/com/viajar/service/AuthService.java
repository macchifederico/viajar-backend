package ar.com.viajar.service;

import ar.com.viajar.domain.User;
import ar.com.viajar.domain.enums.DriverStatus;
import ar.com.viajar.dto.request.LoginRequest;
import ar.com.viajar.dto.request.RegisterRequest;
import ar.com.viajar.dto.request.VerifyOtpRequest;
import ar.com.viajar.dto.response.AuthResponse;
import ar.com.viajar.dto.response.TokensResponse;
import ar.com.viajar.dto.response.UserResponse;
import ar.com.viajar.exception.AppException;
import ar.com.viajar.integration.OtpService;
import ar.com.viajar.repository.UserRepository;
import ar.com.viajar.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) throw AppException.conflict("Email ya registrado");
        if (userRepository.existsByPhone(req.phone())) throw AppException.conflict("Teléfono ya registrado");

        User user = new User();
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPhone(req.phone());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(req.role());

        if (req.role() != ar.com.viajar.domain.enums.UserRole.passenger) {
            user.setDriverStatus(DriverStatus.pending_documents);
        }

        user = userRepository.save(user);
        otpService.generate(req.phone());

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        log.info("Login attempt for email: {}", req.email());
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(AppException::unauthorized);

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw AppException.unauthorized();
        }

        return buildAuthResponse(user);
    }

    public TokensResponse refresh(String refreshToken) {
        UUID userId = jwtUtil.extractUserIdFromRefresh(refreshToken);
        User user = userRepository.findById(userId).orElseThrow(AppException::unauthorized);
        return new TokensResponse(
                jwtUtil.generateAccessToken(user.getId(), user.getRole()),
                jwtUtil.generateRefreshToken(userId)
        );
    }

    public void verifyOtp(VerifyOtpRequest req) {
        if (!otpService.verify(req.phone(), req.code())) {
            throw AppException.badRequest("Código inválido o expirado");
        }
        userRepository.findByPhone(req.phone()).ifPresent(u -> {
            u.setVerifiedAt(Instant.now());
            userRepository.save(u);
        });
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(
                jwtUtil.generateAccessToken(user.getId(), user.getRole()),
                jwtUtil.generateRefreshToken(user.getId()),
                UserResponse.from(user)
        );
    }
}
