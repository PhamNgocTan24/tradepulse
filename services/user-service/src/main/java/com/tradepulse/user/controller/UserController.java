package com.tradepulse.user.controller;

import com.tradepulse.common.dto.response.ApiResponse;
import com.tradepulse.user.dto.request.UpdateProfileRequest;
import com.tradepulse.user.dto.response.LeaderboardEntry;
import com.tradepulse.user.dto.response.UserProfileResponse;
import com.tradepulse.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(userId, request)));
    }

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(userService.getBalance(userId)));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<ApiResponse<List<LeaderboardEntry>>> getLeaderboard(
            @RequestParam(defaultValue = "50") int top) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getLeaderboard(Math.min(top, 100))));
    }
}
