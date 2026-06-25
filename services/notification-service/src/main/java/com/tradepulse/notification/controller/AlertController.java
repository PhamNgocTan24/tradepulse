package com.tradepulse.notification.controller;

import com.tradepulse.common.dto.response.ApiResponse;
import com.tradepulse.notification.dto.request.CreateAlertRequest;
import com.tradepulse.notification.dto.response.AlertResponse;
import com.tradepulse.notification.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @PostMapping
    public ResponseEntity<ApiResponse<AlertResponse>> createAlert(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAlertRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(alertService.createAlert(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AlertResponse>>> getAlerts(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(alertService.getAlerts(userId)));
    }

    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> deleteAlert(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String alertId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        alertService.deleteAlert(alertId, userId);
        return ResponseEntity.noContent().build();
    }
}
