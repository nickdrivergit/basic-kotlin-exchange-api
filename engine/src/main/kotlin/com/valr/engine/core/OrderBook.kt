package com.valr.engine.core

import com.valr.engine.model.Level
import com.valr.engine.model.Order
import com.valr.engine.model.OrderBookSnapshot
import com.valr.engine.model.Side
import com.valr.engine.model.Trade
import java.math.BigDecimal
import java.util.ArrayDeque
import java.util.TreeMap

class OrderBook(val symbol: String) {
    private val bids = TreeMap<BigDecimal, ArrayDeque<Order>>(compareByDescending { it })
    private val asks = TreeMap<BigDecimal, ArrayDeque<Order>>(compareBy { it })
    private val tradesHistory = mutableListOf<Trade>()

    fun snapshot(): OrderBookSnapshot {
        val bidLevels =
                bids.entries.map { (price, orders) -> Level(price, orders.sumOf { it.remaining }) }
        val askLevels =
                asks.entries.map { (price, orders) -> Level(price, orders.sumOf { it.remaining }) }

        return OrderBookSnapshot(symbol = symbol, bids = bidLevels, asks = askLevels)
    }

    fun placeOrder(order: Order): List<Trade> {
        validate(order)

        // Match against opposite side
        val trades =
                when (order.side) {
                    Side.BUY -> match(order, asks)
                    Side.SELL -> match(order, bids)
                }

        // If not fully filled, rest on own side
        when (order.side) {
            Side.BUY -> rest(order, bids)
            Side.SELL -> rest(order, asks)
        }

        tradesHistory.addAll(trades)
        return trades
    }

    fun getTrades(limit: Int = 50): List<Trade> = tradesHistory.takeLast(limit).reversed()

    // -------------------------
    // Private helpers
    // -------------------------

    private fun validate(order: Order) {
        require(order.quantity > BigDecimal.ZERO) { "Order quantity must be positive" }
        require(order.remaining > BigDecimal.ZERO) { "Remaining quantity must be positive" }
        require(order.price > BigDecimal.ZERO) { "Order price must be positive" }
        require(order.symbol == symbol) { "Order symbol must match this order book ($symbol)" }
    }

    private fun match(
            order: Order,
            oppositeBook: TreeMap<BigDecimal, ArrayDeque<Order>>
    ): List<Trade> {
        val trades = mutableListOf<Trade>()

        val oppositeOrders =
                when (order.side) {
                    Side.BUY -> oppositeBook.headMap(order.price, true)
                    Side.SELL -> oppositeBook.tailMap(order.price, true)
                }

        val iterator = oppositeOrders.entries.iterator()

        while (order.remaining > BigDecimal.ZERO && iterator.hasNext()) {
            // Kept first var as price for clarity instead of `_`
            val (price, queue) = iterator.next()

            // FIFO matching at this price level
            while (order.remaining > BigDecimal.ZERO && queue.isNotEmpty()) {
                val maker = queue.first()
                val tradeQty = minOf(order.remaining, maker.remaining)

                trades +=
                        Trade(
                                price = maker.price,
                                quantity = tradeQty,
                                takerOrderId = order.id,
                                makerOrderId = maker.id,
                                timestamp = System.currentTimeMillis()
                        )

                order.remaining -= tradeQty
                maker.remaining -= tradeQty

                if (maker.remaining == BigDecimal.ZERO) queue.removeFirst()
            }

            if (queue.isEmpty()) iterator.remove()
        }

        return trades
    }

    private fun rest(order: Order, book: TreeMap<BigDecimal, ArrayDeque<Order>>) {
        if (order.remaining > BigDecimal.ZERO) {
            book.computeIfAbsent(order.price) { ArrayDeque() }.add(order)
        }
    }
}
