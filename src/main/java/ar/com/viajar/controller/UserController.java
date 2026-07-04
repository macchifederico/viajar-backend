package ar.com.viajar.controller;

import ar.com.viajar.dto.response.ApiResponse;
import ar.com.viajar.dto.response.UserResponse;
import ar.com.viajar.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal UUID userId) {
        return new ApiResponse<>(userService.getMe(userId));
    }

    @PutMapping(value = "/me", consumes = {"multipart/form-data", "application/json"})
    public ApiResponse<UserResponse> updateMe(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String name,
            @RequestPart(name = "avatar", required = false) MultipartFile avatar
    ) {
        return new ApiResponse<>(userService.updateMe(userId, name, avatar));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getPublicProfile(@PathVariable UUID id) {
        return new ApiResponse<>(userService.getPublicProfile(id));
    }
}
