package com.valr.engine

import com.valr.engine.core.OrderBook
import com.valr.engine.model.Order
import com.valr.engine.model.Side
import org.openjdk.jmh.annotations.*
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class OrderBookBenchmark {
    private lateinit var ob: OrderBook
    private var counter = 0

    // Parameterized snapshot depths
    @Param("10", "100", "1000")
    var depth: Int = 100

    // Parameterized sweep size (# of price levels per iteration)
    @Param("10", "100", "1000")
    var sweepLevels: Int = 100

    @Setup(Level.Iteration)
    fun setup() {
        ob = OrderBook("BTCZAR")
        counter = 0
    }

    @Benchmark
    fun placeRestingOrder() {
        ob.placeOrder(
            Order("b$counter", "BTCZAR", Side.BUY,
                BigDecimal("950000"), BigDecimal.ONE, BigDecimal.ONE)
        )
        counter++
    }

    @Benchmark
    fun matchSingleTrade() {
        ob.placeOrder(Order("s$counter", "BTCZAR", Side.SELL,
            BigDecimal("950000"), BigDecimal.ONE, BigDecimal.ONE))
        ob.placeOrder(Order("b$counter", "BTCZAR", Side.BUY,
            BigDecimal("950000"), BigDecimal.ONE, BigDecimal.ONE))
        counter++
    }

    @Benchmark
    fun sweepAcrossLevels() {
        repeat(sweepLevels) { i ->
            ob.placeOrder(Order("s$counter-$i", "BTCZAR", Side.SELL,
                BigDecimal(950000 + i), BigDecimal.ONE, BigDecimal.ONE))
        }
        val sweeper = Order("b$counter", "BTCZAR", Side.BUY,
            BigDecimal(950000 + sweepLevels + 10),
            BigDecimal(sweepLevels), BigDecimal(sweepLevels))
        ob.placeOrder(sweeper)
        counter++
    }

    @Benchmark
    fun snapshotParameterized() {
        ob.snapshot(depth = depth)
    }

    @Benchmark
    fun getRecentTrades() {
        ob.getTrades(limit = 50)
    }
}
