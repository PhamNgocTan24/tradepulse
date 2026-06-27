package com.tradepulse.auth.service.impl;

import com.tradepulse.auth.domain.entity.User;
import com.tradepulse.auth.domain.enums.UserRole;
import com.tradepulse.auth.dto.request.LoginRequest;
import com.tradepulse.auth.dto.request.RefreshTokenRequest;
import com.tradepulse.auth.dto.request.RegisterRequest;
import com.tradepulse.auth.dto.response.AuthResponse;
import com.tradepulse.auth.dto.response.TotpSetupResponse;
import com.tradepulse.auth.exception.AuthException;
import com.tradepulse.auth.repository.UserRepository;
import com.tradepulse.auth.service.AuthService;
import com.tradepulse.auth.service.JwtService;
import com.tradepulse.auth.service.TotpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw AuthException.emailAlreadyExists(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .build();

        userRepository.save(user);
        log.info("User registered: userId={}, email={}", user.getId(), user.getEmail());

        // Publish USER_REGISTERED so user-service creates the profile + virtual balance
        // TODO: wrap in transactional outbox pattern for production reliability
        kafkaTemplate.send("user-events", user.getId().toString(),
                new java.util.HashMap<>(java.util.Map.of(
                        "eventType", "USER_REGISTERED",
                        "userId", user.getId().toString(),
                        "email", user.getEmail()
                )));

        return jwtService.generateTokenPair(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(AuthException::invalidCredentials);

        if (!user.isEnabled()) {
            throw AuthException.userDisabled();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw AuthException.invalidCredentials();
        }

        if (user.isTotpEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                throw AuthException.totpRequired();
            }
            if (!totpService.isValid(user.getTotpSecret(), request.totpCode())) {
                throw AuthException.totpInvalid();
            }
        }

        log.info("User logged in: userId={}", user.getId());
        return jwtService.generateTokenPair(user);
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        return jwtService.refreshTokenPair(request.refreshToken());
    }

    @Override
    public void logout(String accessToken) {
        jwtService.blacklistToken(accessToken);
        log.info("Access token blacklisted on logout");
    }

    @Override
    @Transactional
    public TotpSetupResponse setupTotp(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found", org.springframework.http.HttpStatus.NOT_FOUND));
        
        TotpService.TotpGenerateResult result = totpService.generateSetup(user.getEmail());
        String encryptedSecret = totpService.encrypt(result.secretBase32());
        
        user.setTotpSecret(encryptedSecret);
        userRepository.save(user);
        
        log.info("Temporary TOTP secret generated and saved for userId={}", userId);
        return result.setupResponse();
    }

    @Override
    @Transactional
    public void confirmTotp(UUID userId, String totpCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found", org.springframework.http.HttpStatus.NOT_FOUND));

        // The encrypted secret was stored temporarily during setupTotp; validate then persist
        if (!totpService.isValid(user.getTotpSecret(), totpCode)) {
            throw AuthException.totpInvalid();
        }

        user.setTotpEnabled(true);
        userRepository.save(user);
        log.info("TOTP enabled for userId={}", userId);
    }
}
