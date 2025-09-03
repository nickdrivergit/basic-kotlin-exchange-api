package com.valr.engine.model

import java.math.BigDecimal

data class Order(
    val id: String,
    val symbol: String,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    var remaining: BigDecimal,
    val timestamp: Long = System.currentTimeMillis()
)
