package com.valr.engine.model

data class OrderBookSnapshot(
    val symbol: String,
    val bids: List<Level> = emptyList(), // sorted descending (best bid first)
    val asks: List<Level> = emptyList()  // sorted ascending (best ask first)
)
