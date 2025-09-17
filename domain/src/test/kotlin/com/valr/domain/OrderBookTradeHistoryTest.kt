package com.valr.domain

import com.valr.domain.core.OrderBook
import com.valr.domain.model.Order
import com.valr.domain.model.Side
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.math.BigDecimal

class OrderBookTradeHistoryTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `recent trades returned newest first`() {
        val ob = OrderBook("BTCZAR")

        val sell1 = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1"))
        ob.placeOrder(sell1)
        val buy1 = Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1"))
        ob.placeOrder(buy1)

        val sell2 = Order("s2", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1"))
        ob.placeOrder(sell2)
        val buy2 = Order("b2", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1"))
        ob.placeOrder(buy2)

        val trades = ob.getTrades(limit = 2)

        assertEquals(2, trades.size)
        assertEquals("b2", trades.first().takerOrderId)
        assertEquals("s2", trades.first().makerOrderId)
        assertEquals("b1", trades.last().takerOrderId)
        assertEquals("s1", trades.last().makerOrderId)
    }

    @Test
    fun `trade order preserved oldest to newest in history`() {
        val ob = OrderBook("BTCZAR")

        val s1 = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1"))
        val s2 = Order("s2", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1"))
        ob.placeOrder(s1)
        ob.placeOrder(s2)
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("2"), bd("2")))

        val tradesAll = ob.getTrades(limit = 10).asReversed()
        assertEquals(listOf("s1", "s2"), tradesAll.map { it.makerOrderId })
        assertTrue(tradesAll.all { it.takerOrderId == "b1" })
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
        assertEquals("b11999", trades.first().takerOrderId)
        assertEquals("s11999", trades.first().makerOrderId)
    }

    @Test
    fun `getTrades limit smaller than history size returns only requested trades`() {
        val ob = OrderBook("BTCZAR")
        repeat(5) { i ->
            ob.placeOrder(Order("s$i", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1")))
            ob.placeOrder(Order("b$i", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
        }
        val trades = ob.getTrades(limit = 3)
        assertEquals(3, trades.size)
        assertEquals(listOf("b4", "b3", "b2"), trades.map { it.takerOrderId })
    }

    @Test
    fun `getTrades 0 returns empty list`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1")))
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))

        val trades = ob.getTrades(limit = 0)
        assertTrue(trades.isEmpty())
    }

    @Test
    fun `getTrades on empty history returns empty list`() {
        val ob = OrderBook("BTCZAR")
        val trades = ob.getTrades(limit = 10)
        assertTrue(trades.isEmpty())
    }
}

