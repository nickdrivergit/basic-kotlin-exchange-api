package com.valr.engine

import com.valr.engine.core.OrderBook
import com.valr.engine.model.Order
import com.valr.engine.model.Side
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderBookTradeHistoryTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `recent trades returned newest first`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1")))
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
        ob.placeOrder(Order("s2", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1")))
        ob.placeOrder(Order("b2", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
        val trades = ob.getTrades(limit = 2)
        assertEquals(2, trades.size)
        assertEquals("b2", trades.first().takerOrderId)
    }

    @Test
    fun `trade history cap respected`() {
        val ob = OrderBook("BTCZAR")
        repeat(12_000) { i ->
            ob.placeOrder(Order("s$i", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1")))
            ob.placeOrder(Order("b$i", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
        }
        val trades = ob.getTrades(limit = 10_000)
        assertEquals(10_000, trades.size)
    }

    @Test
    fun `trade limit smaller than history size returns only requested trades`() {
        val ob = OrderBook("BTCZAR")
        repeat(20) { i ->
            ob.placeOrder(Order("s$i", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1")))
            ob.placeOrder(Order("b$i", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
        }
        val trades = ob.getTrades(limit = 5)
        assertEquals(5, trades.size)
    }
}
