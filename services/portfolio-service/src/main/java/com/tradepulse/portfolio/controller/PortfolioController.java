package com.tradepulse.portfolio.controller;

import com.tradepulse.common.dto.response.ApiResponse;
import com.tradepulse.common.dto.response.PageResponse;
import com.tradepulse.portfolio.dto.response.PortfolioResponse;
import com.tradepulse.portfolio.dto.response.TransactionResponse;
import com.tradepulse.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/me")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public ResponseEntity<ApiResponse<PortfolioResponse>> getPortfolio(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.getPortfolio(userId)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.getHistory(userId, page, size)));
    }
}
