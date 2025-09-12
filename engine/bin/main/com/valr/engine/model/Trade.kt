package com.valr.engine.model

import java.math.BigDecimal

data class Trade(
    val id: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val takerOrderId: String,
    val makerOrderId: String,
    val timestamp: Long
)
