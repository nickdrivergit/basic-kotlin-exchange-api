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

        fun consumeFromHead(tradeQty: BigDecimal) {
            val maker = orders.first()
            maker.remaining = maker.remaining.subtract(tradeQty)
            totalRemaining = totalRemaining.subtract(tradeQty)
            if (maker.remaining.compareTo(BigDecimal.ZERO) <= 0) {
                orders.removeFirst()
            }
        }

        fun isEmpty(): Boolean = orders.isEmpty()
    }

    // Both maps stored ascending
    private val bids = TreeMap<BigDecimal, PriceLevel>() // best bid = lastKey()
    private val asks = TreeMap<BigDecimal, PriceLevel>() // best ask = firstKey()

    private val maxTrades = 10_000
    private val tradesHistory: ArrayDeque<Trade> = ArrayDeque(maxTrades)

    fun snapshot(depth: Int? = null): OrderBookSnapshot {
        val bidLevels =
                bids.entries
                        .map { (price, level) -> Level(price, level.totalRemaining) }
                        .asReversed() // highest bid first
                        .let { if (depth != null) it.take(depth) else it }

        val askLevels =
                asks.entries.map { (price, level) -> Level(price, level.totalRemaining) }.let {
                    if (depth != null) it.take(depth) else it
                }

        return OrderBookSnapshot(symbol = symbol, bids = bidLevels, asks = askLevels)
    }

    fun submitLimitOrder(
            symbol: String,
            side: Side,
            price: BigDecimal,
            quantity: BigDecimal
    ): Pair<Order, List<Trade>> {
        val order =
                Order(
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

        val trades =
                when (order.side) {
                    Side.BUY -> match(order, asks, true)
                    Side.SELL -> match(order, bids, false)
                }

        if (order.remaining > BigDecimal.ZERO) {
            when (order.side) {
                Side.BUY -> rest(order, bids)
                Side.SELL -> rest(order, asks)
            }
        }

        return trades
    }

    fun getTrades(limit: Int = 50): List<Trade> =
            tradesHistory.toList().takeLast(limit).asReversed()

    // -------------------------
    // Private helpers
    // -------------------------

    private fun validate(order: Order) {
        require(order.quantity > BigDecimal.ZERO) { "Order quantity must be positive" }
        require(order.remaining > BigDecimal.ZERO) { "Remaining quantity must be positive" }
        require(order.remaining <= order.quantity) { "Remaining cannot exceed quantity" }
        require(order.price > BigDecimal.ZERO) { "Order price must be positive" }
        require(order.symbol == symbol) { "Order symbol must match this order book ($symbol)" }
    }

    private fun match(
            order: Order,
            oppositeBook: TreeMap<BigDecimal, PriceLevel>,
            isBuy: Boolean
    ): List<Trade> {
        val trades = mutableListOf<Trade>()

        val eligibleLevels: Map<BigDecimal, PriceLevel> =
                if (isBuy) {
                    // BUY matches asks ≤ buy price (ascending order)
                    oppositeBook.headMap(order.price, true)
                } else {
                    // SELL matches bids ≥ sell price (descending order)
                    oppositeBook.tailMap(order.price, true).descendingMap()
                }

        val toRemove = ArrayList<BigDecimal>()
        val iterator = eligibleLevels.entries.iterator()

        while (order.remaining > BigDecimal.ZERO && iterator.hasNext()) {
            val entry = iterator.next()
            val level = entry.value

            while (order.remaining > BigDecimal.ZERO && !level.isEmpty()) {
                val maker = level.orders.first()
                val tradeQty = minOf(order.remaining, maker.remaining)

                val trade =
                        Trade(
                                id = UUID.randomUUID().toString(),
                                price = maker.price,
                                quantity = tradeQty,
                                takerOrderId = order.id,
                                makerOrderId = maker.id,
                                timestamp = System.currentTimeMillis()
                        )
                trades.add(trade)
                addTrade(trade)

                order.remaining = order.remaining.subtract(tradeQty)
                level.consumeFromHead(tradeQty)

                require(order.remaining >= BigDecimal.ZERO) {
                    "Taker overfilled: ${order.remaining}"
                }
            }

            if (level.isEmpty() || level.totalRemaining == BigDecimal.ZERO) {
                toRemove.add(entry.key)
            }
        }
        
        toRemove.forEach { oppositeBook.remove(it) }

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
