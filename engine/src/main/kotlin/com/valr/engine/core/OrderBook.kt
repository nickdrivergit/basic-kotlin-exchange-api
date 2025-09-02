package com.valr.engine.core

import com.valr.engine.model.*
import java.math.BigDecimal
import java.util.ArrayDeque
import java.util.TreeMap

class OrderBook(val symbol: String) {

    // Best bid = highest price first (descending)
    private val bids = TreeMap<BigDecimal, ArrayDeque<Order>>(compareByDescending { it })

    // Best ask = lowest price first (ascending)
    private val asks = TreeMap<BigDecimal, ArrayDeque<Order>>(compareBy { it })

    // Store trades for trade history (newest last)
    private val trades = mutableListOf<Trade>()

    fun snapshot(): OrderBookSnapshot {
        val bidLevels = bids.entries.map { (price, orders) ->
            Level(price, orders.sumOf { it.remaining })
        }
        val askLevels = asks.entries.map { (price, orders) ->
            Level(price, orders.sumOf { it.remaining })
        }

        return OrderBookSnapshot(
            symbol = symbol,
            bids = bidLevels,
            asks = askLevels
        )
    }

    fun placeOrder(order: Order): List<Trade> {
        val bookSide = if (order.side == Side.BUY) bids else asks
        bookSide.computeIfAbsent(order.price) { ArrayDeque() }.add(order)
        return emptyList()
    }

    fun getTrades(limit: Int = 50): List<Trade> =
        trades.takeLast(limit).reversed()
}
