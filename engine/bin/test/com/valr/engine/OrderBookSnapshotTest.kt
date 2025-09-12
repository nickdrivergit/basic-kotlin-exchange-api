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

    @Test
    fun `non-crossing orders rest on correct sides`() {
        val ob = OrderBook("BTCZAR")
        // Place a bid below any asks
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("940000"), bd("1.0"), bd("1.0")))
        // Place an ask above any bids
        ob.placeOrder(Order("s1", "BTCZAR", Side.SELL, bd("960000"), bd("2.0"), bd("2.0")))

        val snap = ob.snapshot()
        assertEquals(1, snap.bids.size)
        assertEquals(bd("940000"), snap.bids.first().price)
        assertEquals(bd("1.0"), snap.bids.first().quantity)
        assertEquals(1, snap.asks.size)
        assertEquals(bd("960000"), snap.asks.first().price)
        assertEquals(bd("2.0"), snap.asks.first().quantity)
    }

    @Test
    fun `aggregation uses exact BigDecimal totals for mixed scales`() {
        val ob = OrderBook("BTCZAR")
        ob.placeOrder(Order("b1", "BTCZAR", Side.BUY, bd("950000"), bd("0.10"), bd("0.10")))
        ob.placeOrder(Order("b2", "BTCZAR", Side.BUY, bd("950000"), bd("0.2"), bd("0.2")))
        ob.placeOrder(Order("b3", "BTCZAR", Side.BUY, bd("950000"), bd("0.300"), bd("0.300")))
        val snap = ob.snapshot()
        assertEquals(1, snap.bids.size)
        assertEquals(bd("0.600"), snap.bids.first().quantity)
    }

    @Test
    fun `deep snapshot returns correct top-N and does not mutate`() {
        val ob = OrderBook("BTCZAR")
        // 10 bid levels 951000..960000
        (951000..960000 step 1000).forEachIndexed { i, p ->
            ob.placeOrder(Order("b$i", "BTCZAR", Side.BUY, bd(p.toString()), bd("1"), bd("1")))
        }
        // 10 ask levels 961000..970000
        (961000..970000 step 1000).forEachIndexed { i, p ->
            ob.placeOrder(Order("s$i", "BTCZAR", Side.SELL, bd(p.toString()), bd("1"), bd("1")))
        }

        val snap3 = ob.snapshot(depth = 3)
        // Top 3 bids are highest prices
        assertEquals(listOf("960000", "959000", "958000").map { bd(it) }, snap3.bids.map { it.price })
        // Top 3 asks are lowest prices
        assertEquals(listOf("961000", "962000", "963000").map { bd(it) }, snap3.asks.map { it.price })

        // Another snapshot should be identical; calling snapshot must not mutate state
        val snap3b = ob.snapshot(depth = 3)
        assertEquals(snap3, snap3b)
    }
}
