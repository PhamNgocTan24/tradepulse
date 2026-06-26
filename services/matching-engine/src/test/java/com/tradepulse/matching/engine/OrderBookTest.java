package com.tradepulse.matching.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private OrderBook orderBook;
    private final String symbol = "BTCUSDT";

    private void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
        } else {
            assertEquals(0, expected.compareTo(actual), "Expected " + expected + " but got " + actual);
        }
    }

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook(symbol);
    }

    @Test
    void testLimitOrderNoMatch() {
        OrderBookEntry bid = new OrderBookEntry(
                UUID.randomUUID(), UUID.randomUUID(), symbol, "BUY",
                new BigDecimal("60000.00"), new BigDecimal("1.5"), new BigDecimal("1.5"), Instant.now()
        );

        MatchingResult result = orderBook.match(bid, false);

        assertBigDecimalEquals(BigDecimal.ZERO, result.filledQuantity());
        assertFalse(result.fullyFilled());
        assertEquals(1, orderBook.bidCount());
        assertEquals(0, orderBook.askCount());
    }

    @Test
    void testLimitOrderExactMatch() {
        OrderBookEntry ask = new OrderBookEntry(
                UUID.randomUUID(), UUID.randomUUID(), symbol, "SELL",
                new BigDecimal("60000.00"), new BigDecimal("1.0"), new BigDecimal("1.0"), Instant.now()
        );
        orderBook.match(ask, false);

        OrderBookEntry bid = new OrderBookEntry(
                UUID.randomUUID(), UUID.randomUUID(), symbol, "BUY",
                new BigDecimal("60000.00"), new BigDecimal("1.0"), new BigDecimal("1.0"), Instant.now()
        );
        MatchingResult result = orderBook.match(bid, false);

        assertBigDecimalEquals(new BigDecimal("1.0"), result.filledQuantity());
        assertBigDecimalEquals(new BigDecimal("60000.00"), result.averageFillPrice());
        assertTrue(result.fullyFilled());
        assertEquals(0, orderBook.bidCount());
        assertEquals(0, orderBook.askCount());
    }

    @Test
    void testMarketOrderMatchesRestingLimit() {
        OrderBookEntry ask = new OrderBookEntry(
                UUID.randomUUID(), UUID.randomUUID(), symbol, "SELL",
                new BigDecimal("60000.00"), new BigDecimal("1.0"), new BigDecimal("1.0"), Instant.now()
        );
        orderBook.match(ask, false);

        OrderBookEntry bidMarket = new OrderBookEntry(
                UUID.randomUUID(), UUID.randomUUID(), symbol, "BUY",
                BigDecimal.ZERO, new BigDecimal("0.5"), new BigDecimal("0.5"), Instant.now()
        );
        MatchingResult result = orderBook.match(bidMarket, true);

        assertBigDecimalEquals(new BigDecimal("0.5"), result.filledQuantity());
        assertBigDecimalEquals(new BigDecimal("60000.00"), result.averageFillPrice());
        assertTrue(result.fullyFilled());
        assertEquals(0, orderBook.bidCount());
        assertEquals(1, orderBook.askCount());
    }

    @Test
    void testCancelOrder() {
        UUID orderId = UUID.randomUUID();
        OrderBookEntry bid = new OrderBookEntry(
                orderId, UUID.randomUUID(), symbol, "BUY",
                new BigDecimal("60000.00"), new BigDecimal("1.0"), new BigDecimal("1.0"), Instant.now()
        );
        orderBook.match(bid, false);
        assertEquals(1, orderBook.bidCount());

        boolean cancelled = orderBook.cancel(orderId);
        assertTrue(cancelled);
        assertEquals(0, orderBook.bidCount());
    }

    @Test
    void testMatchAgainstPriceUpdates() {
        OrderBookEntry bid = new OrderBookEntry(
                UUID.randomUUID(), UUID.randomUUID(), symbol, "BUY",
                new BigDecimal("60000.00"), new BigDecimal("1.0"), new BigDecimal("1.0"), Instant.now()
        );
        orderBook.match(bid, false);

        OrderBookEntry ask = new OrderBookEntry(
                UUID.randomUUID(), UUID.randomUUID(), symbol, "SELL",
                new BigDecimal("62000.00"), new BigDecimal("2.0"), new BigDecimal("2.0"), Instant.now()
        );
        orderBook.match(ask, false);

        assertEquals(1, orderBook.bidCount());
        assertEquals(1, orderBook.askCount());

        List<MatchingResult> results1 = orderBook.matchAgainstPrice(new BigDecimal("61000.00"));
        assertTrue(results1.isEmpty());
        assertEquals(1, orderBook.bidCount());
        assertEquals(1, orderBook.askCount());

        List<MatchingResult> results2 = orderBook.matchAgainstPrice(new BigDecimal("59500.00"));
        assertEquals(1, results2.size());
        MatchingResult matchedBid = results2.get(0);
        assertEquals(bid.orderId(), matchedBid.orderId());
        assertEquals("BUY", matchedBid.side());
        assertBigDecimalEquals(new BigDecimal("1.0"), matchedBid.filledQuantity());
        assertBigDecimalEquals(new BigDecimal("60000.00"), matchedBid.averageFillPrice());
        assertEquals(0, orderBook.bidCount());
        assertEquals(1, orderBook.askCount());

        List<MatchingResult> results3 = orderBook.matchAgainstPrice(new BigDecimal("62500.00"));
        assertEquals(1, results3.size());
        MatchingResult matchedAsk = results3.get(0);
        assertEquals(ask.orderId(), matchedAsk.orderId());
        assertEquals("SELL", matchedAsk.side());
        assertBigDecimalEquals(new BigDecimal("2.0"), matchedAsk.filledQuantity());
        assertBigDecimalEquals(new BigDecimal("62000.00"), matchedAsk.averageFillPrice());
        assertEquals(0, orderBook.bidCount());
        assertEquals(0, orderBook.askCount());
    }
}
