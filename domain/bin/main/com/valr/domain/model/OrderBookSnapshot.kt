package com.valr.domain.model

data class OrderBookSnapshot(
    val symbol: String,
    val bids: List<Level> = emptyList(),
    val asks: List<Level> = emptyList()
)

