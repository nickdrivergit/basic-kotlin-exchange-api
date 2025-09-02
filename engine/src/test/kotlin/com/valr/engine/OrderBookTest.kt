package com.valr.engine

import com.valr.engine.core.OrderBook
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrderBookTest {

    @Test
    fun `new order book should be empty`() {
        val ob = OrderBook("BTCZAR")
        val snapshot = ob.snapshot()
        assertTrue(snapshot.bids.isEmpty(), "Expected no bids in a new order book")
        assertTrue(snapshot.asks.isEmpty(), "Expected no asks in a new order book")
    }
}
