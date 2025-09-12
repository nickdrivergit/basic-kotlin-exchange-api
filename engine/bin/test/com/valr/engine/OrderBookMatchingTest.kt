package com.valr.engine

import com.valr.engine.core.OrderBook
import com.valr.engine.model.Order
import com.valr.engine.model.Side
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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
        val t = trades.first()
        assertEquals("b1", t.takerOrderId)
        assertEquals("s1", t.makerOrderId)
        assertEquals(bd("1"), t.quantity)
    }

    @Test
    fun `sell matches highest bid first`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
        ob.placeOrder(Order("b2", "BTCZAR", Side.BUY, bd("960000"), bd("1"), bd("1")))

        val sell = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1"))
        val trades = ob.placeOrder(sell)

        assertEquals(1, trades.size)
        val t = trades.first()
        assertEquals("s1", t.takerOrderId)
        assertEquals("b2", t.makerOrderId, "Should match best bid (960000)")
    }

    @Test
    fun `buy matches lowest ask first`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("970000"), bd("1"), bd("1")))
        ob.placeOrder(Order("s2", "BTCZAR", Side.SELL, bd("960000"), bd("1"), bd("1")))

        val buy = Order("b1", "BTCZAR", Side.BUY, bd("970000"), bd("1"), bd("1"))
        val trades = ob.placeOrder(buy)

        assertEquals(1, trades.size)
        val t = trades.first()
        assertEquals("b1", t.takerOrderId)
        assertEquals("s2", t.makerOrderId, "Should match best ask (960000)")
    }

    @Test
    fun `partial fill leaves remainder on book`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1.0"), bd("1.0")))

        val buy = Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("0.4"), bd("0.4"))
        val trades = ob.placeOrder(buy)

        assertEquals(1, trades.size)
        val t = trades.first()
        assertEquals("b1", t.takerOrderId)
        assertEquals("s1", t.makerOrderId)
        assertEquals(bd("0.4"), t.quantity)

        val snap = ob.snapshot()
        assertEquals(bd("0.6"), snap.asks.first().quantity, "0.6 should remain on ask side")
    }

    @Test
    fun `large buy consumes multiple asks from lowest to higher`() {
        val ob = OrderBook("BTCZAR")
        val ask1 = Order("s1", "BTCZAR", Side.SELL, bd("960000"), bd("0.3"), bd("0.3"))
        val ask2 = Order("s2", "BTCZAR", Side.SELL, bd("970000"), bd("0.7"), bd("0.7"))
        ob.placeOrder(ask1)
        ob.placeOrder(ask2)

        val bigBuy = Order("b1", "BTCZAR", Side.BUY, bd("970000"), bd("2.0"), bd("2.0"))
        val trades = ob.placeOrder(bigBuy)

        assertEquals(2, trades.size)
        assertEquals(listOf(bd("960000"), bd("970000")), trades.map { it.price })
        assertEquals(
                listOf("s1", "s2"),
                trades.map { it.makerOrderId },
                "Should match lowest ask first"
        )
        assertTrue(trades.all { it.takerOrderId == "b1" })
    }

    @Test
    fun `large sell consumes multiple bids from highest to lower`() {
        val ob = OrderBook("BTCZAR")
        val bid1 = Order("b1", "BTCZAR", Side.BUY, bd("960000"), bd("0.5"), bd("0.5"))
        val bid2 = Order("b2", "BTCZAR", Side.BUY, bd("950000"), bd("0.5"), bd("0.5"))
        ob.placeOrder(bid1)
        ob.placeOrder(bid2)

        val bigSell = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1.0"), bd("1.0"))
        val trades = ob.placeOrder(bigSell)

        assertEquals(2, trades.size)
        assertEquals(listOf(bd("960000"), bd("950000")), trades.map { it.price })
        assertEquals(
                listOf("b1", "b2"),
                trades.map { it.makerOrderId },
                "Should match highest bid first"
        )
        assertTrue(trades.all { it.takerOrderId == "s1" })
    }

    @Test
    fun `fifo fairness within same price level`() {
        val ob = OrderBook("BTCZAR")
        val bid1 = Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("0.5"), bd("0.5"))
        val bid2 = Order("b2", "BTCZAR", Side.BUY, bd("950000"), bd("0.5"), bd("0.5"))
        ob.placeOrder(bid1)
        ob.placeOrder(bid2)

        val sell = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1.0"), bd("1.0"))
        val trades = ob.placeOrder(sell)

        assertEquals(2, trades.size)
        assertEquals(
                listOf("b1", "b2"),
                trades.map { it.makerOrderId },
                "Earlier resting bid must fill first"
        )
        assertTrue(trades.all { it.takerOrderId == "s1" })
    }

    @Test
    fun `price equality boundaries match correctly`() {
        val ob = OrderBook("BTCZAR")
        // Ask at 960000, Buy at 960000 should match
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("960000"), bd("1"), bd("1")))
        val t1 = ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("960000"), bd("1"), bd("1")))
        assertEquals(1, t1.size)
        // Bid at 950000, Sell at 950000 should match
        ob.placeOrder(Order("b2", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
        val t2 = ob.placeOrder(Order("s2", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1")))
        assertEquals(1, t2.size)
    }

    @Test
    fun `fully consumed level is removed`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
        // One sell that consumes the only bid level
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1")))
        val snap = ob.snapshot()
        assertTrue(snap.bids.isEmpty(), "Bid level should be removed once empty")
    }

    @Test
    fun `partial fill then rest continues FIFO on next taker`() {
        val ob = OrderBook("BTCZAR")
        // Two sells at same price (FIFO: s1, then s2)
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("960000"), bd("0.6"), bd("0.6")))
        ob.placeOrder(Order("s2", "BTCZAR", Side.SELL, bd("960000"), bd("0.5"), bd("0.5")))

        // First taker partially fills s1
        val t1 = ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("960000"), bd("0.4"), bd("0.4")))
        assertEquals(1, t1.size)
        // Next taker should continue with remaining s1 (0.2) then s2
        val t2 = ob.placeOrder(Order("b2", "BTCZAR", Side.BUY, bd("960000"), bd("0.6"), bd("0.6")))
        assertEquals(listOf("s1", "s2"), t2.map { it.makerOrderId })
        assertEquals(listOf(bd("0.2"), bd("0.4")), t2.map { it.quantity })
    }

    @Test
    fun `trade executes at maker price across multiple levels`() {
        val ob = OrderBook("BTCZAR")
        // Makers on ask side at 960000 and 970000
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("960000"), bd("0.3"), bd("0.3")))
        ob.placeOrder(Order("s2", "BTCZAR", Side.SELL, bd("970000"), bd("0.3"), bd("0.3")))
        // Taker crosses with a buy at 990000
        val trades = ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("990000"), bd("0.5"), bd("0.5")))
        // Prices should be makers' prices in order: 960000 then 970000
        assertEquals(listOf(bd("960000"), bd("970000")), trades.map { it.price })
    }

    @Test
    fun `empty levels are removed from order book`() {
        val ob = OrderBook("BTCZAR")

        // Place a resting SELL order at 950000
        val sell = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("1.0"), bd("1.0"))
        ob.placeOrder(sell)

        // Cross it fully with a BUY
        val buy = Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("1.0"), bd("1.0"))
        val trades = ob.placeOrder(buy)

        // Sanity: trade executed
        assertEquals(1, trades.size)
        assertEquals(bd("1.0"), trades.first().quantity)

        // Snapshot should NOT include a 950000 ask with 0 quantity
        val snapshot = ob.snapshot()
        assertTrue(
                snapshot.asks.none { it.price == bd("950000") && it.quantity == bd("0.0") },
                "OrderBook snapshot should not include zero-quantity levels"
        )
    }
}
