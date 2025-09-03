package com.valr.engine

import com.valr.engine.core.OrderBook
import com.valr.engine.model.Order
import com.valr.engine.model.Side
import java.math.BigDecimal
import kotlin.system.measureTimeMillis
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OrderBookPerformanceTest {
    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `insert 1 million resting orders`() {
        val ob = OrderBook("BTCZAR")
        val n = 1_000_000
        val ms = measureTimeMillis {
            repeat(n) { i ->
                ob.placeOrder(Order("b$i", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
            }
        }
        println("Inserted $n resting orders in ${ms}ms")
        val snap = ob.snapshot()
        assertTrue(snap.bids.isNotEmpty())
        assertEquals(n.toBigDecimal(), snap.bids.first().quantity)
    }

    @Test
    fun `sweep across 50k price levels correctness`() {
        val ob = OrderBook("BTCZAR")
        val levels = 50_000
        repeat(levels) { i ->
            ob.placeOrder(
                    Order("s$i", "BTCZAR", Side.SELL, bd((950000 + i).toString()), bd("1"), bd("1"))
            )
        }

        val sweeper =
                Order(
                        "b-sweep",
                        "BTCZAR",
                        Side.BUY,
                        bd((950000 + levels + 10).toString()),
                        bd("200000"),
                        bd("200000")
                )

        val ms = measureTimeMillis {
            val trades = ob.placeOrder(sweeper)
            assertEquals(levels, trades.size, "Expected to trade against all $levels asks")
            assertTrue(
                    ob.snapshot().asks.all { it.quantity == BigDecimal.ZERO },
                    "All asks should be consumed"
            )
        }
        println("Swept $levels price levels in ${ms}ms")
    }

    @Test
    fun `deep snapshot stress test`() {
        val ob = OrderBook("BTCZAR")
        repeat(100_000) { i ->
            ob.placeOrder(
                    Order(
                            "b$i",
                            "BTCZAR",
                            Side.BUY,
                            bd((950000 + (i % 100)).toString()),
                            bd("1"),
                            bd("1")
                    )
            )
        }
        val ms = measureTimeMillis { ob.snapshot(depth = 10_000) }
        println("Snapshot of 10k levels built in ${ms}ms")
        assertTrue(ob.snapshot(10_000).bids.size <= 10_000)
    }

    @Test
    fun `trade history cap under heavy load`() {
        val ob = OrderBook("BTCZAR")
        repeat(12_000) { i ->
            ob.placeOrder(Order("s$i", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("1")))
            ob.placeOrder(Order("b$i", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
        }

        val trades = ob.getTrades(limit = 50_000)
        assertEquals(10_000, trades.size, "Trade history should be capped at 10k")
        assertEquals("b11999", trades.first().takerOrderId)
        assertEquals("s11999", trades.first().makerOrderId)
    }

    @Test
    fun `fifo fairness under scale`() {
        val ob = OrderBook("BTCZAR")
        repeat(10_000) { i ->
            ob.placeOrder(Order("b$i", "BTCZAR", Side.BUY, bd("950000"), bd("1"), bd("1")))
        }
        val bigSell = Order("s1", "BTCZAR", Side.SELL, bd("950000"), bd("10000"), bd("10000"))
        val trades = ob.placeOrder(bigSell)

        assertEquals("b0", trades.first().makerOrderId, "First resting bid must match first")
        assertEquals("b9999", trades.last().makerOrderId, "Last resting bid must match last")
        assertEquals(BigDecimal.ZERO, bigSell.remaining, "Sell should have fully matched")
        assertEquals(10_000, trades.size, "Should have matched all 10k resting bids")
    }
}
