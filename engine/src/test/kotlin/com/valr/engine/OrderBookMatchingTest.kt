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
        assertEquals(bd("1"), trades.first().quantity)
        val snap = ob.snapshot()
        assertTrue(snap.bids.isEmpty() && snap.asks.isEmpty(), "Both orders filled")
    }

    @Test
    fun `sell matches highest bid first`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
        ob.placeOrder(Order("b2", "BTCZAR", Side.BUY, bd("960000"), bd("1"), bd("1"))) // better price

        val sell = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1"))
        val trades = ob.placeOrder(sell)

        assertEquals(1, trades.size)
        assertEquals(bd("960000"), trades.first().price, "Should match best bid (960000) first")
    }

    @Test
    fun `buy matches lowest ask first`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("970000"), bd("1"), bd("1")))
        ob.placeOrder(Order("s2", "BTCZAR", Side.SELL, bd("960000"), bd("1"), bd("1"))) // better price

        val buy = Order("b1", "BTCZAR", Side.BUY, bd("970000"), bd("1"), bd("1"))
        val trades = ob.placeOrder(buy)

        assertEquals(1, trades.size)
        assertEquals(bd("960000"), trades.first().price, "Should match best ask (960000) first")
    }

    @Test
    fun `partial fill leaves remainder on book`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1.0"), bd("1.0")))

        val buy = Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("0.4"), bd("0.4"))
        ob.placeOrder(buy)

        val snap = ob.snapshot()
        assertEquals(bd("0.6"), snap.asks.first().quantity, "0.6 should remain on ask side")
        assertTrue(snap.bids.isEmpty())
    }

    @Test
    fun `large buy consumes multiple asks from lowest to higher`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("960000"), bd("0.3"), bd("0.3")))
        ob.placeOrder(Order("s2", "BTCZAR", Side.SELL, bd("970000"), bd("0.7"), bd("0.7")))

        val bigBuy = Order("b1", "BTCZAR", Side.BUY, bd("970000"), bd("2.0"), bd("2.0"))
        val trades = ob.placeOrder(bigBuy)

        assertEquals(2, trades.size)
        assertEquals(listOf(bd("960000"), bd("970000")), trades.map { it.price }, "Should match lower ask (960000) first")
        assertEquals(bd("1.0"), trades.sumOf { it.quantity })
        val snap = ob.snapshot()
        assertTrue(snap.bids.isNotEmpty(), "Unfilled remainder should rest on bids")
    }

    @Test
    fun `large sell consumes multiple bids from highest to lower`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("960000"), bd("0.5"), bd("0.5")))
        ob.placeOrder(Order("b2", "BTCZAR", Side.BUY, bd("950000"), bd("0.5"), bd("0.5")))

        val bigSell = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1.0"), bd("1.0"))
        val trades = ob.placeOrder(bigSell)

        assertEquals(2, trades.size)
        assertEquals(listOf(bd("960000"), bd("950000")), trades.map { it.price }, "Should match higher bid (960000) first")
        assertEquals(bd("1.0"), trades.sumOf { it.quantity })
        val snap = ob.snapshot()
        assertTrue(snap.asks.isEmpty(), "No asks should remain")
    }
}
