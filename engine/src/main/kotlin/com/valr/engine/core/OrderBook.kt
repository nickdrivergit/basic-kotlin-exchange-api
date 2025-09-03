package com.valr.engine.core

import com.valr.engine.model.Level
import com.valr.engine.model.Order
import com.valr.engine.model.OrderBookSnapshot
import com.valr.engine.model.Side
import com.valr.engine.model.Trade
import java.math.BigDecimal
import java.util.ArrayDeque
import java.util.TreeMap
import java.util.UUID

class OrderBook(val symbol: String) {

    private data class PriceLevel(
        val orders: ArrayDeque<Order> = ArrayDeque(),
        var totalRemaining: BigDecimal = BigDecimal.ZERO
    ) {
        fun addOrder(order: Order) {
            orders.addLast(order)
            totalRemaining = totalRemaining.add(order.remaining)
        }

        /** Consume from head (FIFO) and safely remove the order if <= 0 due to scale differences. */
        fun consumeFromHead(tradeQty: BigDecimal) {
            val maker = orders.first()
            maker.remaining = maker.remaining.subtract(tradeQty)
            totalRemaining = totalRemaining.subtract(tradeQty)

            // Use compareTo to avoid scale-sensitive equals() issues with BigDecimal
            if (maker.remaining.compareTo(BigDecimal.ZERO) <= 0) {
                orders.removeFirst()
            }
        }

        fun isEmpty(): Boolean = orders.isEmpty()
    }

    private val bids = TreeMap<BigDecimal, PriceLevel>()
    private val asks = TreeMap<BigDecimal, PriceLevel>()

    private val maxTrades = 10_000 // configurable cap for bounded memory
    private val tradesHistory: ArrayDeque<Trade> = ArrayDeque(maxTrades)

    fun snapshot(depth: Int? = null): OrderBookSnapshot {
        val bidLevels = bids.entries
            .map { (price, level) -> Level(price, level.totalRemaining) }
            .asReversed()
            .let { if (depth != null) it.take(depth) else it }

        val askLevels = asks.entries
            .map { (price, level) -> Level(price, level.totalRemaining) }
            .let { if (depth != null) it.take(depth) else it }

        return OrderBookSnapshot(symbol = symbol, bids = bidLevels, asks = askLevels)
    }

    fun submitLimitOrder(
        symbol: String,
        side: Side,
        price: BigDecimal,
        quantity: BigDecimal
    ): Pair<Order, List<Trade>> {
        val order = Order(
            id = UUID.randomUUID().toString(),
            symbol = symbol,
            side = side,
            price = price,
            quantity = quantity,
            remaining = quantity
        )
        val trades = placeOrder(order)
        return order to trades
    }

    fun placeOrder(order: Order): List<Trade> {
        validate(order)

        val trades = when (order.side) {
            Side.BUY  -> match(order, asks)
            Side.SELL -> match(order, bids)
        }

        if (order.remaining.compareTo(BigDecimal.ZERO) > 0) {
            when (order.side) {
                Side.BUY  -> rest(order, bids)
                Side.SELL -> rest(order, asks)
            }
        }

        return trades
    }

    fun getTrades(limit: Int = 50): List<Trade> =
        // Convert to List to use takeLast; newest-first for API ergonomics
        tradesHistory.toList().takeLast(limit).asReversed()

    // -------------------------
    // Private helpers
    // -------------------------

    private fun validate(order: Order) {
        require(order.quantity.compareTo(BigDecimal.ZERO) > 0) { "Order quantity must be positive" }
        require(order.remaining.compareTo(BigDecimal.ZERO) > 0) { "Remaining quantity must be positive" }
        require(order.remaining.compareTo(order.quantity) <= 0) { "Remaining cannot exceed quantity" }
        require(order.price.compareTo(BigDecimal.ZERO) > 0) { "Order price must be positive" }
        require(order.symbol == symbol) { "Order symbol must match this order book ($symbol)" }
    }

    private fun match(
        order: Order,
        oppositeBook: TreeMap<BigDecimal, PriceLevel>
    ): List<Trade> {
        val trades = mutableListOf<Trade>()

        val eligibleLevels = when (order.side) {
            // BUY can match any ask <= order.price (best ask upwards)
            Side.BUY  -> oppositeBook.headMap(order.price, true)
            // SELL can match any bid >= order.price (best bid downwards)
            Side.SELL -> oppositeBook.tailMap(order.price, true)
        }

        val iterator = eligibleLevels.entries.iterator()

        while (order.remaining.compareTo(BigDecimal.ZERO) > 0 && iterator.hasNext()) {
            val entry = iterator.next()
            val level = entry.value

            while (order.remaining.compareTo(BigDecimal.ZERO) > 0 && !level.isEmpty()) {
                val maker = level.orders.first()
                val tradeQty = minOf(order.remaining, maker.remaining)

                val trade = Trade(
                    id = UUID.randomUUID().toString(),
                    price = maker.price, // trades execute at maker price
                    quantity = tradeQty,
                    takerOrderId = order.id,
                    makerOrderId = maker.id,
                    timestamp = System.currentTimeMillis()
                )
                trades.add(trade)
                addTrade(trade)

                // Update quantities (use compareTo checks for safety)
                order.remaining = order.remaining.subtract(tradeQty)
                level.consumeFromHead(tradeQty)

                // Sanity checks to fail fast if logic regresses
                require(order.remaining.compareTo(BigDecimal.ZERO) >= 0) { "Taker overfilled: ${order.remaining}" }
            }

            if (level.isEmpty()) {
                iterator.remove()
            }
        }

        return trades
    }

    private fun rest(order: Order, book: TreeMap<BigDecimal, PriceLevel>) {
        book.computeIfAbsent(order.price) { PriceLevel() }.addOrder(order)
    }

    private fun addTrade(trade: Trade) {
        if (tradesHistory.size == maxTrades) {
            tradesHistory.removeFirst()
        }
        tradesHistory.addLast(trade)
    }
}
