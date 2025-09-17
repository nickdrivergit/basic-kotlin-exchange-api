package com.valr.application

import com.valr.domain.core.OrderBook
import com.valr.domain.model.*
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

interface OrderBookStore {
    fun get(symbol: String): OrderBook
}

class OrderMatchingService(private val store: OrderBookStore) {
    private val locks = ConcurrentHashMap<String, Any>()

    private fun lockFor(symbol: String): Any =
        locks.computeIfAbsent(symbol.uppercase()) { Any() }

    fun snapshot(symbol: String, depth: Int?): OrderBookSnapshot =
        synchronized(lockFor(symbol)) { store.get(symbol).snapshot(depth) }

    fun submitLimitOrder(
        symbol: String,
        side: Side,
        price: BigDecimal,
        quantity: BigDecimal,
        timeInForce: TimeInForce
    ): Pair<Order, List<Trade>> =
        synchronized(lockFor(symbol)) {
            store.get(symbol).submitLimitOrder(symbol, side, price, quantity, timeInForce)
        }

    fun getTrades(symbol: String, limit: Int): List<Trade> =
        synchronized(lockFor(symbol)) { store.get(symbol).getTrades(limit) }
}
