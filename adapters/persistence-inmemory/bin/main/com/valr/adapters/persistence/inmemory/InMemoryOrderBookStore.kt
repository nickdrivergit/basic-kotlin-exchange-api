package com.valr.adapters.persistence.inmemory

import com.valr.application.OrderBookStore
import com.valr.domain.core.OrderBook
import java.util.concurrent.ConcurrentHashMap

class InMemoryOrderBookStore : OrderBookStore {
    private val books = ConcurrentHashMap<String, OrderBook>()

    override fun get(symbol: String): OrderBook =
        books.computeIfAbsent(symbol.uppercase()) { OrderBook(it) }
}

