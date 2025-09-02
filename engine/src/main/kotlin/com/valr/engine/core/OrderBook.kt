package com.valr.engine

data class OrderBookSnapshot(
    val bids: List<String> = emptyList(),
    val asks: List<String> = emptyList()
)

class OrderBook(val symbol: String) {
    fun snapshot(): OrderBookSnapshot {
        return OrderBookSnapshot()
    }
}
