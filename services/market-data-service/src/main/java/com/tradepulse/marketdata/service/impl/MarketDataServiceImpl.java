package com.tradepulse.marketdata.service.impl;

import com.tradepulse.common.dto.kafka.market.MarketDataEvent;
import com.tradepulse.marketdata.domain.entity.MarketTick;
import com.tradepulse.marketdata.domain.model.PriceInfo;
import com.tradepulse.marketdata.event.producer.MarketDataProducer;
import com.tradepulse.marketdata.repository.MarketTickRepository;
import com.tradepulse.marketdata.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {

    private static final String PRICE_KEY_PREFIX = "price:";

    @Value("${redis.price-ttl-seconds:30}")
    private long priceTtlSeconds;

    private final MarketTickRepository marketTickRepository;
    private final MarketDataProducer marketDataProducer;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate stompTemplate;

    @Override
    public void processTick(MarketTick tick) {
        // 1. Persist to MongoDB
        marketTickRepository.save(tick);

        // 2. Cache current price in Redis (TTL 30s)
        redisTemplate.opsForValue().set(
                PRICE_KEY_PREFIX + tick.getSymbol(),
                tick.getPrice().toPlainString(),
                Duration.ofSeconds(priceTtlSeconds)
        );

        // 3. Publish to Kafka for matching-engine and notification-service
        MarketDataEvent event = new MarketDataEvent(
                tick.getSymbol(), tick.getPrice(), tick.getVolume24h(),
                tick.getPriceChangePercent24h(), tick.getHighPrice24h(),
                tick.getLowPrice24h(), tick.getTimestamp()
        );
        marketDataProducer.publish(event);

        // 4. Push via STOMP WebSocket to subscribed clients
        stompTemplate.convertAndSend("/topic/prices/" + tick.getSymbol(), event);

        log.debug("Tick processed: symbol={}, price={}", tick.getSymbol(), tick.getPrice());
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        String raw = redisTemplate.opsForValue().get(PRICE_KEY_PREFIX + symbol.toUpperCase());
        if (raw == null) return BigDecimal.ZERO;
        return new BigDecimal(raw);
    }

    @Override
    public PriceInfo getPriceInfo(String symbol) {
        // Redis only stores the raw price; full snapshot comes from most recent tick
        List<MarketTick> recent = marketTickRepository
                .findBySymbolOrderByTimestampDesc(symbol.toUpperCase(), PageRequest.of(0, 1));

        if (recent.isEmpty()) return null;
        MarketTick tick = recent.get(0);

        // Always use Redis for the price itself (authoritative, TTL 30s)
        BigDecimal livePrice = getCurrentPrice(symbol);

        return new PriceInfo(tick.getSymbol(),
                livePrice.compareTo(BigDecimal.ZERO) > 0 ? livePrice : tick.getPrice(),
                tick.getVolume24h(), tick.getPriceChangePercent24h(),
                tick.getHighPrice24h(), tick.getLowPrice24h(), Instant.now());
    }

    @Override
    public List<MarketTick> getTickHistory(String symbol, int limit) {
        return marketTickRepository.findBySymbolOrderByTimestampDesc(
                symbol.toUpperCase(), PageRequest.of(0, Math.min(limit, 500)));
    }
}
