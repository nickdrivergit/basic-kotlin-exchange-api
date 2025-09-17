package com.valr.perf

import com.valr.adapters.persistence.inmemory.InMemoryOrderBookStore
import com.valr.application.OrderMatchingService
import com.valr.domain.model.Side
import com.valr.domain.model.TimeInForce
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class OrderBookBenchmark {
    private lateinit var service: OrderMatchingService
    private val symbol = "BTCZAR"
    private var counter = 0

    @Param("10", "100", "1000")
    var depth: Int = 100

    @Param("10", "100", "1000")
    var sweepLevels: Int = 100

    @Setup(Level.Iteration)
    fun setup() {
        service = OrderMatchingService(InMemoryOrderBookStore())
        counter = 0
        // preload some trades to make getTrades non-trivial
        repeat(2000) { i ->
            val price = BigDecimal("950000")
            service.submitLimitOrder(symbol, Side.SELL, price, BigDecimal.ONE, TimeInForce.GTC)
            service.submitLimitOrder(symbol, Side.BUY, price, BigDecimal.ONE, TimeInForce.GTC)
        }
    }

    @Benchmark
    fun placeRestingOrder() {
        service.submitLimitOrder(symbol, Side.BUY,
            BigDecimal("950000"), BigDecimal.ONE, TimeInForce.GTC)
        counter++
    }

    @Benchmark
    fun matchSingleTrade() {
        val price = BigDecimal("950000")
        service.submitLimitOrder(symbol, Side.SELL, price, BigDecimal.ONE, TimeInForce.GTC)
        service.submitLimitOrder(symbol, Side.BUY, price, BigDecimal.ONE, TimeInForce.GTC)
        counter++
    }

    @Benchmark
    fun sweepAcrossLevels() {
        repeat(sweepLevels) { i ->
            service.submitLimitOrder(symbol, Side.SELL,
                BigDecimal(950000 + i), BigDecimal.ONE, TimeInForce.GTC)
        }
        service.submitLimitOrder(symbol, Side.BUY,
            BigDecimal(950000 + sweepLevels + 10), BigDecimal(sweepLevels.toString()), TimeInForce.GTC)
        counter++
    }

    @Benchmark
    fun snapshotParameterized(bh: Blackhole) {
        bh.consume(service.snapshot(symbol, depth))
    }

    @Benchmark
    fun getRecentTrades(bh: Blackhole) {
        bh.consume(service.getTrades(symbol, 50))
    }
}

