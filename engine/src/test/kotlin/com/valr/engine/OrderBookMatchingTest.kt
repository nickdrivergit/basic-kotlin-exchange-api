package com.valr.engine

import com.valr.engine.core.OrderBook
import com.valr.engine.model.Order
import com.valr.engine.model.Side
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderBookMatchingTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `crossing orders trade immediately`() {
        val ob = OrderBook("BTCZAR")
        val sell = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1"))
        ob.placeOrder(sell)
        val buy = Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1"))
        val trades = ob.placeOrder(buy)
        assertEquals(1, trades.size)
        assertEquals(BigDecimal.ONE, trades.first().quantity)
        val snap = ob.snapshot()
        assertTrue(snap.bids.isEmpty() && snap.asks.isEmpty(), "Both filled")
    }

    @Test
    fun `partial fill leaves remainder`() {
        val ob = OrderBook("BTCZAR")
        val sell = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1.0"), bd("1.0"))
        ob.placeOrder(sell)
        val buy = Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("0.4"), bd("0.4"))
        ob.placeOrder(buy)
        val snap = ob.snapshot()
        assertEquals(bd("0.6"), snap.asks.first().quantity)
        assertTrue(snap.bids.isEmpty())
    }

    @Test
    fun `large buy consumes multiple asks`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("0.3"), bd("0.3")))
        ob.placeOrder(Order("s2", "BTCZAR", Side.SELL, bd("950000"), bd("0.7"), bd("0.7")))
        val bigBuy = Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("2.0"), bd("2.0"))
        val trades = ob.placeOrder(bigBuy)
        assertEquals(2, trades.size)
        assertEquals(bd("1.0"), trades.sumOf { it.quantity })
        val snap = ob.snapshot()
        assertTrue(snap.bids.isNotEmpty(), "Remainder should rest on book")
    }

    @Test
    fun `sell crossing multiple bids`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("960000"), bd("0.5"), bd("0.5")))
        ob.placeOrder(Order("b2", "BTCZAR", Side.BUY, bd("950000"), bd("0.5"), bd("0.5")))
        val sell = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1.0"), bd("1.0"))
        val trades = ob.placeOrder(sell)
        assertEquals(2, trades.size)
        assertEquals(bd("1.0"), trades.sumOf { it.quantity })
        val snap = ob.snapshot()
        assertTrue(snap.asks.isEmpty())
    }
}
