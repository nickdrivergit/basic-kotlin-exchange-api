package com.valr.domain.core

import com.valr.domain.model.*
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

    private val bids = TreeMap<BigDecimal, PriceLevel>()
    private val asks = TreeMap<BigDecimal, PriceLevel>()

    private val maxTrades = 10_000
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
        quantity: BigDecimal,
        timeInForce: TimeInForce = TimeInForce.GTC
    ): Pair<Order, List<Trade>> {
        val order = Order(
            id = UUID.randomUUID().toString(),
            symbol = symbol,
            side = side,
            price = price,
            quantity = quantity,
            remaining = quantity,
            timeInForce = timeInForce
        )
        val trades = placeOrder(order)
        return order to trades
    }

    fun placeOrder(order: Order): List<Trade> {
        validate(order)

        if (order.timeInForce == TimeInForce.FOK) {
            val canFillAll = when (order.side) {
                Side.BUY -> canFullyFill(order, asks, isBuy = true)
                Side.SELL -> canFullyFill(order, bids, isBuy = false)
            }
            if (!canFillAll) return emptyList()
        }

        val trades = when (order.side) {
            Side.BUY -> match(order, asks, true)
            Side.SELL -> match(order, bids, false)
        }

        when (order.timeInForce) {
            TimeInForce.GTC -> {
                if (order.remaining > BigDecimal.ZERO) {
                    when (order.side) {
                        Side.BUY -> rest(order, bids)
                        Side.SELL -> rest(order, asks)
                    }
                }
            }
            TimeInForce.IOC, TimeInForce.FOK -> { /* no rest */ }
        }

        return trades
    }

    fun getTrades(limit: Int = 50): List<Trade> = tradesHistory.toList().takeLast(limit).asReversed()

    private fun validate(order: Order) {
        require(order.quantity > BigDecimal.ZERO) { "Order quantity must be positive" }
        require(order.remaining > BigDecimal.ZERO) { "Remaining quantity must be positive" }
        require(order.remaining <= order.quantity) { "Remaining cannot exceed quantity" }
        require(order.price > BigDecimal.ZERO) { "Order price must be positive" }
        require(order.symbol == symbol) { "Order symbol must match this order book ($symbol)" }
    }

    private fun match(
        order: Order,
        oppositeSide: TreeMap<BigDecimal, PriceLevel>,
        isBuy: Boolean
    ): List<Trade> {
        val trades = mutableListOf<Trade>()
        val eligibleLevels: Map<BigDecimal, PriceLevel> = if (isBuy) {
            oppositeSide.headMap(order.price, true)
        } else {
            oppositeSide.tailMap(order.price, true).descendingMap()
        }

        val toRemove = ArrayList<BigDecimal>()
        val iterator = eligibleLevels.entries.iterator()
        while (order.remaining > BigDecimal.ZERO && iterator.hasNext()) {
            val entry = iterator.next()
            val level = entry.value
            while (order.remaining > BigDecimal.ZERO && !level.isEmpty()) {
                val maker = level.orders.first()
                val tradeQty = minOf(order.remaining, maker.remaining)
                val trade = Trade(
                    id = UUID.randomUUID().toString(),
                    price = maker.price,
                    quantity = tradeQty,
                    takerOrderId = order.id,
                    makerOrderId = maker.id,
                    timestamp = System.currentTimeMillis()
                )
                trades.add(trade)
                addTradeToBook(trade)
                order.remaining = order.remaining.subtract(tradeQty)
                level.consumeFromHead(tradeQty)
                require(order.remaining >= BigDecimal.ZERO) { "Taker overfilled: ${order.remaining}" }
            }
            if (level.isEmpty() || level.totalRemaining == BigDecimal.ZERO) {
                toRemove.add(entry.key)
            }
        }
        toRemove.forEach { oppositeSide.remove(it) }
        return trades
    }

    private fun rest(order: Order, book: TreeMap<BigDecimal, PriceLevel>) {
        book.computeIfAbsent(order.price) { PriceLevel() }.addOrder(order)
    }

    private fun addTradeToBook(trade: Trade) {
        if (tradesHistory.size == maxTrades) tradesHistory.removeFirst()
        tradesHistory.addLast(trade)
    }

    private fun canFullyFill(
        order: Order,
        oppositeBook: TreeMap<BigDecimal, PriceLevel>,
        isBuy: Boolean
    ): Boolean {
        val eligible: Map<BigDecimal, PriceLevel> = if (isBuy) {
            oppositeBook.headMap(order.price, true)
        } else {
            oppositeBook.tailMap(order.price, true)
        }
        if (eligible.isEmpty()) return false
        var total = BigDecimal.ZERO
        for ((_, level) in eligible) {
            total = total.add(level.totalRemaining)
            if (total >= order.quantity) return true
        }
        return total >= order.quantity
    }
}

