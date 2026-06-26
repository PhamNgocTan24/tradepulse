package com.tradepulse.matching.service;

import com.tradepulse.common.dto.kafka.order.OrderEvent;
import com.tradepulse.common.dto.kafka.order.OrderEventType;
import com.tradepulse.matching.engine.MatchingResult;
import com.tradepulse.matching.engine.OrderBook;
import com.tradepulse.matching.engine.OrderBookEntry;
import com.tradepulse.matching.event.producer.OrderResultProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core matching service. Maintains one OrderBook per symbol in a ConcurrentHashMap.
 * Synchronises on the per-symbol OrderBook instance to ensure thread safety at
 * symbol level without a global lock.
 *
 * Hot path: no DB calls. All state is in-memory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final ConcurrentHashMap<String, OrderBook> books = new ConcurrentHashMap<>();
    private final OrderResultProducer resultProducer;

    /** Entry point for NEW_ORDER events from Kafka. */
    public void processNewOrder(OrderEvent event) {
        long start = System.nanoTime();

        OrderBook book = books.computeIfAbsent(event.symbol(), OrderBook::new);
        boolean isMarket = "MARKET".equalsIgnoreCase(event.orderType());

        OrderBookEntry entry = new OrderBookEntry(
                event.orderId(), event.userId(), event.symbol(), event.side(),
                event.price() != null ? event.price() : BigDecimal.ZERO,
                event.quantity(), event.quantity(), Instant.now()
        );

        MatchingResult result;
        synchronized (book) {
            result = book.match(entry, isMarket);
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.debug("Matched orderId={} in {}ms — filled={}/{}", event.orderId(),
                elapsedMs, result.filledQuantity(), event.quantity());

        resultProducer.publishResult(result, event);
    }

    /** Entry point for CANCEL_ORDER events from Kafka. */
    public void processCancelOrder(OrderEvent event) {
        OrderBook book = books.get(event.symbol());
        if (book == null) return;

        boolean removed;
        synchronized (book) {
            removed = book.cancel(event.orderId());
        }

        if (removed) {
            resultProducer.publishCancelled(event);
            log.info("Order cancelled in book: orderId={}", event.orderId());
        } else {
            log.warn("Cancel requested but order not in book: orderId={}", event.orderId());
        }
    }

    /**
     * Called on market-data tick — evaluates whether any resting limit orders
     * can now be matched at the new market price.
     * This is a lightweight re-evaluation; the OrderBook.match() handles the logic.
     */
    public void onPriceUpdate(String symbol, BigDecimal newPrice) {
        OrderBook book = books.get(symbol);
        if (book == null) return;

        java.util.List<MatchingResult> results;
        synchronized (book) {
            results = book.matchAgainstPrice(newPrice);
        }

        for (MatchingResult result : results) {
            resultProducer.publishLimitOrderFill(result);
        }
    }

    public int getBidCount(String symbol) {
        OrderBook book = books.get(symbol);
        return book != null ? book.bidCount() : 0;
    }

    public int getAskCount(String symbol) {
        OrderBook book = books.get(symbol);
        return book != null ? book.askCount() : 0;
    }
}
