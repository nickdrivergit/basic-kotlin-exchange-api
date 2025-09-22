package com.valr.application

import com.valr.domain.core.OrderBook
import com.valr.domain.model.Side
import com.valr.domain.model.TimeInForce
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

class OrderMatchingServiceTest {

    private class TestStore : OrderBookStore {
        private val books = ConcurrentHashMap<String, OrderBook>()
        override fun get(symbol: String): OrderBook = books.computeIfAbsent(symbol.uppercase()) { OrderBook(it) }
    }

    @Test
    fun `submit and snapshot basic flow`() {
        val service = OrderMatchingService(TestStore())
        val symbol = "SMOKEA"
        service.submitLimitOrder(symbol, Side.BUY, BigDecimal("100"), BigDecimal("1"), TimeInForce.GTC)
        val snap = service.snapshot(symbol, null)
        assertEquals(symbol, snap.symbol)
        assertEquals(1, snap.bids.size)
        assertTrue(snap.asks.isEmpty())
    }

    @Test
    fun `symbol isolation across service`() {
        val service = OrderMatchingService(TestStore())
        val s1 = "AAAUSD"
        val s2 = "BBBUSD"
        service.submitLimitOrder(s1, Side.BUY, BigDecimal("100"), BigDecimal("1"), TimeInForce.GTC)
        val snap = service.snapshot(s2, null)
        assertTrue(snap.bids.isEmpty())
        assertTrue(snap.asks.isEmpty())
    }
}
