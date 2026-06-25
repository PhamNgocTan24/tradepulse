package com.tradepulse.marketdata.controller;

import com.tradepulse.common.dto.response.ApiResponse;
import com.tradepulse.marketdata.domain.entity.MarketTick;
import com.tradepulse.marketdata.domain.model.PriceInfo;
import com.tradepulse.marketdata.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/prices")
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<PriceInfo>> getPrice(@PathVariable String symbol) {
        PriceInfo info = marketDataService.getPriceInfo(symbol.toUpperCase());
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    @GetMapping("/{symbol}/current")
    public ResponseEntity<ApiResponse<BigDecimal>> getCurrentPrice(@PathVariable String symbol) {
        return ResponseEntity.ok(ApiResponse.ok(
                marketDataService.getCurrentPrice(symbol.toUpperCase())));
    }

    @GetMapping("/{symbol}/history")
    public ResponseEntity<ApiResponse<List<MarketTick>>> getHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                marketDataService.getTickHistory(symbol.toUpperCase(), limit)));
    }
}
