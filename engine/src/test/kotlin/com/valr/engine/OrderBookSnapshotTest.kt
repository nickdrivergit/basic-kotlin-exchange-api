package com.valr.engine

import com.valr.engine.core.OrderBook
import com.valr.engine.model.Order
import com.valr.engine.model.Side
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderBookSnapshotTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `placing buy adds to bids`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("1", "BTCZAR", Side.BUY, bd("950000"), bd("0.5"), bd("0.5")))
        val snap = ob.snapshot()
        assertEquals(1, snap.bids.size)
        assertEquals(bd("950000"), snap.bids.first().price) // best bid
        assertEquals(bd("0.5"), snap.bids.first().quantity)
        assertTrue(snap.asks.isEmpty())
    }

    @Test
    fun `placing sell adds to asks`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("2", "BTCZAR", Side.SELL, bd("960000"), bd("1"), bd("1")))
        val snap = ob.snapshot()
        assertEquals(1, snap.asks.size)
        assertEquals(bd("960000"), snap.asks.first().price) // best ask
        assertEquals(bd("1"), snap.asks.first().quantity)
        assertTrue(snap.bids.isEmpty())
    }

    @Test
    fun `orders at same price aggregate`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("1", "BTCZAR", Side.BUY, bd("950000"), bd("0.3"), bd("0.3")))
        ob.placeOrder(Order("2", "BTCZAR", Side.BUY, bd("950000"), bd("0.7"), bd("0.7")))
        val snap = ob.snapshot()
        assertEquals(1, snap.bids.size)
        assertEquals(bd("1.0"), snap.bids.first().quantity)
    }

    @Test
    fun `bids sorted descending in snapshot`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("1", "BTCZAR", Side.BUY, bd("950000"), bd("0.5"), bd("0.5")))
        ob.placeOrder(Order("2", "BTCZAR", Side.BUY, bd("960000"), bd("0.5"), bd("0.5")))
        val snap = ob.snapshot()
        // Snapshot reverses bids → highest price first
        assertEquals(bd("960000"), snap.bids.first().price)
        assertEquals(bd("950000"), snap.bids.last().price)
    }

    @Test
    fun `asks sorted ascending in snapshot`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("1", "BTCZAR", Side.SELL, bd("970000"), bd("0.5"), bd("0.5")))
        ob.placeOrder(Order("2", "BTCZAR", Side.SELL, bd("960000"), bd("0.5"), bd("0.5")))
        val snap = ob.snapshot()
        // Asks remain ascending → lowest price first
        assertEquals(bd("960000"), snap.asks.first().price)
        assertEquals(bd("970000"), snap.asks.last().price)
    }

    @Test
    fun `snapshot depth limits output`() {
        val ob = OrderBook("BTCZAR")
        for (i in 1..5) {
            ob.placeOrder(Order("b$i", "BTCZAR", Side.BUY, bd("95000$i"), bd("1"), bd("1")))
        }
        val snap = ob.snapshot(depth = 2)
        assertEquals(2, snap.bids.size) // only 2 best bids returned
    }
}
