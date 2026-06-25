package com.tradepulse.matching.engine;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * In-memory per-symbol order book.
 *
 * Data structures (O(log n) matching):
 *   Bids — max-heap (highest price first, ties by earliest timestamp)
 *   Asks — min-heap (lowest price first, ties by earliest timestamp)
 *
 * This class is NOT thread-safe. The owning MatchingService synchronises on symbol.
 * Per CLAUDE.md: never persist to DB; rebuilt from Kafka replay on startup.
 */
@Slf4j
public class OrderBook {

    private final String symbol;

    // Bids: max-heap — highest bid price first; tie-break: earliest timestamp
    private final PriorityQueue<OrderBookEntry> bids = new PriorityQueue<>(
            Comparator.comparing(OrderBookEntry::price).reversed()
                    .thenComparing(OrderBookEntry::timestamp)
    );

    // Asks: min-heap — lowest ask price first; tie-break: earliest timestamp
    private final PriorityQueue<OrderBookEntry> asks = new PriorityQueue<>(
            Comparator.comparing(OrderBookEntry::price)
                    .thenComparing(OrderBookEntry::timestamp)
    );

    // Fast lookup for cancel operations
    private final Map<UUID, OrderBookEntry> entriesById = new HashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Attempts to match an incoming order against resting orders.
     * Returns a MatchingResult with all fills. Unfilled remainder stays in the book (LIMIT)
     * or is discarded (MARKET).
     */
    public MatchingResult match(OrderBookEntry incoming, boolean isMarketOrder) {
        List<MatchingResult.FillDetail> fills = new ArrayList<>();
        BigDecimal remaining = incoming.remainingQuantity();
        BigDecimal totalValue = BigDecimal.ZERO;

        PriorityQueue<OrderBookEntry> opposite = "BUY".equals(incoming.side()) ? asks : bids;

        while (remaining.compareTo(BigDecimal.ZERO) > 0 && !opposite.isEmpty()) {
            OrderBookEntry best = opposite.peek();

            boolean priceMatches = isMarketOrder
                    || ("BUY".equals(incoming.side())
                    ? incoming.price().compareTo(best.price()) >= 0
                    : incoming.price().compareTo(best.price()) <= 0);

            if (!priceMatches) break;

            opposite.poll();
            entriesById.remove(best.orderId());

            BigDecimal fillQty = remaining.min(best.remainingQuantity());
            BigDecimal fillPrice = best.price(); // price-time priority: maker price wins

            fills.add(new MatchingResult.FillDetail(
                    best.orderId(), best.userId(), fillQty, fillPrice));
            totalValue = totalValue.add(fillQty.multiply(fillPrice));
            remaining = remaining.subtract(fillQty);

            BigDecimal makerRemaining = best.remainingQuantity().subtract(fillQty);
            if (makerRemaining.compareTo(BigDecimal.ZERO) > 0) {
                OrderBookEntry partial = best.withRemaining(makerRemaining);
                opposite.offer(partial);
                entriesById.put(partial.orderId(), partial);
            }
        }

        BigDecimal filled = incoming.remainingQuantity().subtract(remaining);
        BigDecimal avgPrice = filled.compareTo(BigDecimal.ZERO) > 0
                ? totalValue.divide(filled, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // If partially or not filled at all and it's a LIMIT order — add to book
        if (remaining.compareTo(BigDecimal.ZERO) > 0 && !isMarketOrder) {
            OrderBookEntry resting = incoming.withRemaining(remaining);
            addToBook(resting);
        }

        return new MatchingResult(
                incoming.orderId(), incoming.userId(), symbol,
                filled, avgPrice, remaining.compareTo(BigDecimal.ZERO) == 0, fills);
    }

    /** Removes a resting order (cancel). Returns true if found and removed. */
    public boolean cancel(UUID orderId) {
        OrderBookEntry entry = entriesById.remove(orderId);
        if (entry == null) return false;
        ("BUY".equals(entry.side()) ? bids : asks).remove(entry);
        return true;
    }

    public int bidCount()  { return bids.size(); }
    public int askCount()  { return asks.size(); }
    public String symbol() { return symbol; }

    private void addToBook(OrderBookEntry entry) {
        ("BUY".equals(entry.side()) ? bids : asks).offer(entry);
        entriesById.put(entry.orderId(), entry);
    }
}
