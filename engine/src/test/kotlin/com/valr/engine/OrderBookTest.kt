package com.valr.engine

import com.valr.engine.core.OrderBook
import com.valr.engine.model.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrderBookTest {

        @Test
        fun `new order book should be empty`() {
                val ob = OrderBook("BTCZAR")
                val snapshot = ob.snapshot()
                assertTrue(snapshot.bids.isEmpty(), "Expected no bids in a new order book")
                assertTrue(snapshot.asks.isEmpty(), "Expected no asks in a new order book")
        }

        @Test
        fun `placing a sell order with negative quantity should throw`() {
                val ob = OrderBook("BTCZAR")
                val badOrder =
                        Order(
                                id = "bad-1",
                                symbol = "BTCZAR",
                                side = Side.SELL,
                                price = java.math.BigDecimal("960000"),
                                quantity = java.math.BigDecimal("-1.0"),
                                remaining = java.math.BigDecimal("-1.0"),
                                timestamp = System.currentTimeMillis()
                        )

                val exception =
                        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                                ob.placeOrder(badOrder)
                        }

                assertTrue(
                        exception.message!!.contains("quantity"),
                        "Expected error message to mention quantity"
                )
        }

        @Test
        fun `placing a buy order with negative quantity should throw`() {
                val ob = OrderBook("BTCZAR")
                val badOrder =
                        Order(
                                id = "bad-2",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("960000"),
                                quantity = java.math.BigDecimal("-1.0"),
                                remaining = java.math.BigDecimal("-1.0"),
                                timestamp = System.currentTimeMillis()
                        )

                val exception =
                        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                                ob.placeOrder(badOrder)
                        }

                assertTrue(
                        exception.message!!.contains("quantity"),
                        "Expected error message to mention quantity"
                )
        }

        @Test
        fun `placing a sell order with negative price should throw`() {
                val ob = OrderBook("BTCZAR")
                val badOrder =
                        Order(
                                id = "bad-3",
                                symbol = "BTCZAR",
                                side = Side.SELL,
                                price = java.math.BigDecimal("-960000"),
                                quantity = java.math.BigDecimal("1.0"),
                                remaining = java.math.BigDecimal("1.0"),
                                timestamp = System.currentTimeMillis()
                        )

                val exception =
                        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                                ob.placeOrder(badOrder)
                        }

                assertTrue(
                        exception.message!!.contains("price"),
                        "Expected error message to mention price"
                )
        }

        @Test
        fun `placing a buy order with negative price should throw`() {
                val ob = OrderBook("BTCZAR")
                val badOrder =
                        Order(
                                id = "bad-4",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("-960000"),
                                quantity = java.math.BigDecimal("1.0"),
                                remaining = java.math.BigDecimal("1.0"),
                                timestamp = System.currentTimeMillis()
                        )

                val exception =
                        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                                ob.placeOrder(badOrder)
                        }

                assertTrue(
                        exception.message!!.contains("price"),
                        "Expected error message to mention price"
                )
        }

        @Test
        fun `placing a buy order adds it to bids`() {
                val ob = OrderBook("BTCZAR")
                val order =
                        Order(
                                id = "1",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("950000"),
                                quantity = java.math.BigDecimal("0.5"),
                                remaining = java.math.BigDecimal("0.5"),
                                timestamp = System.currentTimeMillis()
                        )

                ob.placeOrder(order)

                val snapshot = ob.snapshot()
                assertTrue(snapshot.bids.isNotEmpty(), "Expected bids not to be empty")
                assertTrue(snapshot.asks.isEmpty(), "Expected asks still to be empty")
                assertTrue(
                        snapshot.bids.first().quantity == order.remaining,
                        "Expected bid quantity to equal order remaining"
                )
                assertTrue(
                        snapshot.bids.first().price == order.price,
                        "Expected bid price to equal order price"
                )
        }

        @Test
        fun `placing a sell order adds it to asks`() {
                val ob = OrderBook("BTCZAR")
                val order =
                        Order(
                                id = "2",
                                symbol = "BTCZAR",
                                side = Side.SELL,
                                price = java.math.BigDecimal("960000"),
                                quantity = java.math.BigDecimal("1.0"),
                                remaining = java.math.BigDecimal("1.0"),
                                timestamp = System.currentTimeMillis()
                        )

                ob.placeOrder(order)

                val snapshot = ob.snapshot()
                assertTrue(snapshot.asks.isNotEmpty(), "Expected asks not to be empty")
                assertTrue(snapshot.bids.isEmpty(), "Expected bids still to be empty")
                assertTrue(
                        snapshot.asks.first().quantity == order.remaining,
                        "Expected ask quantity to equal order remaining"
                )
                assertTrue(
                        snapshot.asks.first().price == order.price,
                        "Expected ask price to equal order price"
                )
        }

        @Test
        fun `multiple orders at the same price aggregate in snapshot`() {
                val ob = OrderBook("BTCZAR")

                val order1 =
                        Order(
                                id = "agg-1",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("950000"),
                                quantity = java.math.BigDecimal("0.3"),
                                remaining = java.math.BigDecimal("0.3"),
                                timestamp = System.currentTimeMillis()
                        )

                val order2 =
                        Order(
                                id = "agg-2",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("950000"),
                                quantity = java.math.BigDecimal("0.7"),
                                remaining = java.math.BigDecimal("0.7"),
                                timestamp = System.currentTimeMillis()
                        )

                ob.placeOrder(order1)
                ob.placeOrder(order2)

                val snapshot = ob.snapshot()
                assertTrue(snapshot.bids.size == 1, "Expected only one price level at 950000")
                assertTrue(snapshot.bids.first().price == java.math.BigDecimal("950000"))
                assertTrue(
                        snapshot.bids.first().quantity == java.math.BigDecimal("1.0"),
                        "Expected quantities to be aggregated"
                )
        }

        @Test
        fun `multiple bid orders at the different prices do not aggregate in snapshot`() {
                val ob = OrderBook("BTCZAR")

                val order1 =
                        Order(
                                id = "mul-1",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("950000"),
                                quantity = java.math.BigDecimal("0.3"),
                                remaining = java.math.BigDecimal("0.3"),
                                timestamp = System.currentTimeMillis()
                        )

                val order2 =
                        Order(
                                id = "mul-2",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("950001"),
                                quantity = java.math.BigDecimal("0.7"),
                                remaining = java.math.BigDecimal("0.7"),
                                timestamp = System.currentTimeMillis()
                        )

                ob.placeOrder(order1)
                ob.placeOrder(order2)

                val snapshot = ob.snapshot()
                assertTrue(snapshot.bids.size == 2, "Expected two price levels")
        }

        @Test
        fun `aggregated bid orders in snapshot are sorted descending`() {
                val ob = OrderBook("BTCZAR")

                val order1 =
                        Order(
                                id = "mul-1",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("950000"),
                                quantity = java.math.BigDecimal("0.3"),
                                remaining = java.math.BigDecimal("0.3"),
                                timestamp = System.currentTimeMillis()
                        )

                val order2 =
                        Order(
                                id = "mul-2",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("950001"),
                                quantity = java.math.BigDecimal("0.7"),
                                remaining = java.math.BigDecimal("0.7"),
                                timestamp = System.currentTimeMillis()
                        )

                ob.placeOrder(order1)
                ob.placeOrder(order2)

                val snapshot = ob.snapshot()
                assertTrue(snapshot.bids.first().price == java.math.BigDecimal("950001"))
                assertTrue(snapshot.bids.last().price == java.math.BigDecimal("950000"))
                assertTrue(
                        snapshot.bids.first().quantity == java.math.BigDecimal("0.7"),
                        "Expected quantity found"
                )
                assertTrue(
                        snapshot.bids.last().quantity == java.math.BigDecimal("0.3"),
                        "Expected quantity found"
                )
        }

        @Test
        fun `snapshot aggregates bid orders at same price with multiple levels`() {
                val ob = OrderBook("BTCZAR")

                val order1 =
                        Order(
                                id = "mul-1",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("950000"),
                                quantity = java.math.BigDecimal("0.3"),
                                remaining = java.math.BigDecimal("0.3"),
                                timestamp = System.currentTimeMillis()
                        )

                val order2 =
                        Order(
                                id = "mul-2",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("950001"),
                                quantity = java.math.BigDecimal("0.7"),
                                remaining = java.math.BigDecimal("0.7"),
                                timestamp = System.currentTimeMillis()
                        )

                val order3 =
                        Order(
                                id = "mul-3",
                                symbol = "BTCZAR",
                                side = Side.BUY,
                                price = java.math.BigDecimal("950001"),
                                quantity = java.math.BigDecimal("0.3"),
                                remaining = java.math.BigDecimal("0.3"),
                                timestamp = System.currentTimeMillis()
                        )

                ob.placeOrder(order1)
                ob.placeOrder(order2)
                ob.placeOrder(order3)

                val snapshot = ob.snapshot()
                assertTrue(snapshot.bids.size == 2, "Expected two price levels")
        }
}
