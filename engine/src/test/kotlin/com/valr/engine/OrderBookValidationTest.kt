package com.valr.engine

import com.valr.engine.core.OrderBook
import com.valr.engine.model.Order
import com.valr.engine.model.Side
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderBookValidationTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `new order book should be empty`() {
        val ob = OrderBook("BTCZAR")
        val snapshot = ob.snapshot()
        assertTrue(snapshot.bids.isEmpty())
        assertTrue(snapshot.asks.isEmpty())
    }

    @Test
    fun `negative quantity should throw`() {
        val ob = OrderBook("BTCZAR")
        val bad = Order("id", "BTCZAR", Side.BUY, bd("950000"), bd("-1"), bd("-1"))
        val ex = assertThrows(IllegalArgumentException::class.java) { ob.placeOrder(bad) }
        assertTrue(ex.message!!.contains("quantity"))
    }

    @Test
    fun `negative price should throw`() {
        val ob = OrderBook("BTCZAR")
        val bad = Order("id", "BTCZAR", Side.SELL, bd("-1"), bd("1"), bd("1"))
        val ex = assertThrows(IllegalArgumentException::class.java) { ob.placeOrder(bad) }
        assertTrue(ex.message!!.contains("price"))
    }

    @Test
    fun `remaining greater than quantity should throw`() {
        val ob = OrderBook("BTCZAR")
        val bad = Order("id", "BTCZAR", Side.SELL, bd("950000"), bd("1"), bd("2"))
        assertThrows(IllegalArgumentException::class.java) { ob.placeOrder(bad) }
    }

    @Test
    fun `wrong symbol should throw`() {
        val ob = OrderBook("BTCZAR")
        val bad = Order("id", "ETHZAR", Side.BUY, bd("950000"), bd("1"), bd("1"))
        assertThrows(IllegalArgumentException::class.java) { ob.placeOrder(bad) }
    }
}
