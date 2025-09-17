package com.valr.domain.model

import java.math.BigDecimal

data class Order(
    val id: String,
    val symbol: String,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    var remaining: BigDecimal,
    val timestamp: Long = System.currentTimeMillis(),
    val timeInForce: TimeInForce = TimeInForce.GTC
)

