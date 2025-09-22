package com.valr.domain

import com.valr.domain.core.OrderBook
import com.valr.domain.model.Order
import com.valr.domain.model.Side
import com.valr.domain.model.TimeInForce
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderBookSellTimeInForceTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `IOC sell matches immediately and cancels remainder (no rest)`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("100"), bd("0.3"), bd("0.3")))

        val trades = ob.placeOrder(
            Order("s1", "BTCZAR", Side.SELL, bd("100"), bd("0.5"), bd("0.5"), timeInForce = TimeInForce.IOC)
        )
        assertEquals(1, trades.size)
        val snap = ob.snapshot()
        assertTrue(snap.asks.isEmpty(), "IOC remainder should not rest on ask side")
    }

    @Test
    fun `FOK sell not fully fillable cancels with no trades and no mutation`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("100"), bd("0.3"), bd("0.3")))

        val trades = ob.placeOrder(
            Order("s1", "BTCZAR", Side.SELL, bd("100"), bd("0.5"), bd("0.5"), timeInForce = TimeInForce.FOK)
        )
        assertTrue(trades.isEmpty())
        val snap = ob.snapshot()
        assertTrue(snap.asks.isEmpty())
        assertEquals(1, snap.bids.size)
        assertEquals(bd("0.3"), snap.bids.first().quantity)
    }

    @Test
    fun `FOK sell fully fillable executes entirely with no rest`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("100"), bd("0.3"), bd("0.3")))
        ob.placeOrder(Order("b2", "BTCZAR", Side.BUY, bd("101"), bd("0.2"), bd("0.2")))

        val trades = ob.placeOrder(
            Order("s1", "BTCZAR", Side.SELL, bd("100"), bd("0.5"), bd("0.5"), timeInForce = TimeInForce.FOK)
        )
        assertEquals(2, trades.size)
        val snap = ob.snapshot()
        assertTrue(snap.asks.isEmpty())
        assertTrue(snap.bids.isEmpty())
    }
}

