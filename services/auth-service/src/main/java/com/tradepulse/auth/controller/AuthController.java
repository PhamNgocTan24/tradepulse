package com.tradepulse.auth.controller;

import com.tradepulse.auth.dto.request.LoginRequest;
import com.tradepulse.auth.dto.request.RefreshTokenRequest;
import com.tradepulse.auth.dto.request.RegisterRequest;
import com.tradepulse.auth.dto.response.AuthResponse;
import com.tradepulse.auth.dto.response.TotpSetupResponse;
import com.tradepulse.auth.service.AuthService;
import com.tradepulse.auth.service.JwtService;
import com.tradepulse.common.dto.response.ApiResponse;
import com.tradepulse.security.constants.SecurityConstants;
import com.tradepulse.security.jwt.JwtClaims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final java.security.PublicKey jwtPublicKey;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(SecurityConstants.AUTH_HEADER) String authHeader) {
        String token = SecurityConstants.BEARER_PREFIX.strip().isEmpty()
                ? authHeader
                : authHeader.replace(SecurityConstants.BEARER_PREFIX, "");
        authService.logout(token.trim());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/totp/setup")
    public ResponseEntity<ApiResponse<TotpSetupResponse>> setupTotp(
            @RequestHeader(SecurityConstants.AUTH_HEADER) String authHeader) {
        JwtClaims claims = jwtService.parseAndValidate(
                authHeader.replace(SecurityConstants.BEARER_PREFIX, ""));
        return ResponseEntity.ok(ApiResponse.ok(authService.setupTotp(claims.userId())));
    }

    @PostMapping("/totp/confirm")
    public ResponseEntity<Void> confirmTotp(
            @RequestHeader(SecurityConstants.AUTH_HEADER) String authHeader,
            @RequestParam String code) {
        JwtClaims claims = jwtService.parseAndValidate(
                authHeader.replace(SecurityConstants.BEARER_PREFIX, ""));
        authService.confirmTotp(claims.userId(), code);
        return ResponseEntity.noContent().build();
    }

    /**
     * JWKS endpoint — exposes the RS256 public key so the Gateway can validate tokens
     * without calling auth-service on every request.
     */
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Object> jwks() {
        if (jwtPublicKey instanceof java.security.interfaces.RSAPublicKey rsaKey) {
            java.util.Map<String, Object> jwk = java.util.Map.of(
                "kty", "RSA",
                "alg", "RS256",
                "use", "sig",
                "kid", "tradepulse-jwt-key",
                "n", base64UrlEncodeUnsigned(rsaKey.getModulus()),
                "e", base64UrlEncodeUnsigned(rsaKey.getPublicExponent())
            );
            return ResponseEntity.ok(java.util.Map.of("keys", java.util.List.of(jwk)));
        }
        return ResponseEntity.ok(java.util.Map.of("keys", java.util.List.of()));
    }

    private String base64UrlEncodeUnsigned(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 0 && bytes[0] == 0) {
            byte[] tmp = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, tmp, 0, tmp.length);
            bytes = tmp;
        }
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
