package com.valr.engine.model

enum class TimeInForce {
    GTC, // Good-Till-Cancelled (default)
    IOC, // Immediate-Or-Cancel (no rest)
    FOK  // Fill-Or-Kill (all-or-nothing)
}

