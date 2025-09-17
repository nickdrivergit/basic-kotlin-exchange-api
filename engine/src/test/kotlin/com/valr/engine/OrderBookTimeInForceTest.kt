package com.valr.engine

import com.valr.engine.core.OrderBook
import com.valr.engine.model.Order
import com.valr.engine.model.Side
import com.valr.engine.model.TimeInForce
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderBookTimeInForceTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `IOC matches immediately and cancels remainder (no rest)`() {
        val ob = OrderBook("BTCZAR")
        // Rest 0.3 on ask at 100
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("100"), bd("0.3"), bd("0.3")))

        // IOC buy 0.5 @ 100 should match 0.3 and not rest 0.2
        val trades = ob.placeOrder(
                Order("b1", "BTCZAR", Side.BUY, bd("100"), bd("0.5"), bd("0.5"), timeInForce = TimeInForce.IOC)
        )
        assertEquals(1, trades.size)
        // Snapshot should have no bids (remainder 0.2 must not rest)
        val snap = ob.snapshot()
        assertTrue(snap.bids.isEmpty(), "IOC remainder should not rest on book")
    }

    @Test
    fun `FOK not fully fillable cancels with no trades and no mutation`() {
        val ob = OrderBook("BTCZAR")
        // Only 0.3 available on asks at or below 100
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("100"), bd("0.3"), bd("0.3")))

        val trades = ob.placeOrder(
                Order("b1", "BTCZAR", Side.BUY, bd("100"), bd("0.5"), bd("0.5"), timeInForce = TimeInForce.FOK)
        )
        assertTrue(trades.isEmpty(), "FOK should produce no trades if not fully fillable")
        // Book must remain unchanged (ask still there, no bids added)
        val snap = ob.snapshot()
        assertTrue(snap.bids.isEmpty())
        assertEquals(1, snap.asks.size)
        assertEquals(bd("0.3"), snap.asks.first().quantity)
    }

    @Test
    fun `FOK fully fillable executes entirely with no rest`() {
        val ob = OrderBook("BTCZAR")
        // 0.3 @ 100 and 0.2 @ 99 are eligible for buy @100
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("100"), bd("0.3"), bd("0.3")))
        ob.placeOrder(Order("s2", "BTCZAR", Side.SELL, bd("99"), bd("0.2"), bd("0.2")))

        val trades = ob.placeOrder(
                Order("b1", "BTCZAR", Side.BUY, bd("100"), bd("0.5"), bd("0.5"), timeInForce = TimeInForce.FOK)
        )
        assertEquals(2, trades.size)
        val snap = ob.snapshot()
        // No remaining bids or asks at those exact levels
        assertTrue(snap.bids.isEmpty())
        assertTrue(snap.asks.isEmpty())
    }
}

