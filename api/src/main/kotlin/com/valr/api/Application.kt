package com.valr.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.valr.engine.core.OrderBook
import com.valr.engine.model.Side
import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.LoggerHandler
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap


// ---- DTOs ----
data class SubmitOrderRequest(
    val side: String,
    val price: BigDecimal,
    val quantity: BigDecimal
) {
    fun toSide(): Side =
        try { Side.valueOf(side.uppercase()) }
        catch (e: IllegalArgumentException) { throw IllegalArgumentException("side must be BUY or SELL") }
}

data class ErrorResponse(val error: String, val details: String? = null)

object OrderBooks {
    // One book per symbol; kept on the event-loop thread so engine structures stay safe
    private val books = ConcurrentHashMap<String, OrderBook>()
    fun of(symbol: String): OrderBook = books.computeIfAbsent(symbol.uppercase()) { OrderBook(it) }
}

class ApiVerticle : AbstractVerticle() {
    override fun start() {
        // Global JSON config
        DatabindCodec.mapper()
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)

        val router = Router.router(vertx)
        router.route().handler(LoggerHandler.create())
        router.route().handler(BodyHandler.create())

        // Health
        router.get("/healthz").handler { ctx -> ctx.response().end("ok") }

        // Get snapshot: /api/v1/orderbook/:symbol?depth=50
        router.get("/api/v1/orderbook/:symbol").handler { ctx ->
            try {
                val symbol = ctx.pathParam("symbol")
                val depth = ctx.queryParam("depth").firstOrNull()?.toIntOrNull()
                val snapshot = OrderBooks.of(symbol).snapshot(depth)
                ctx.json(snapshot)
            } catch (e: Exception) {
                fail(ctx, 400, e)
            }
        }

        // Submit limit order: /api/v1/orders/:symbol
        // Body: { "side": "BUY|SELL", "price": "123.45", "quantity": "1.234" }
        router.post("/api/v1/orders/:symbol").handler { ctx ->
            try {
                val symbol = ctx.pathParam("symbol")
                val req = ctx.bodyAsJson.mapTo(SubmitOrderRequest::class.java)
                val side = req.toSide()

                val (order, trades) = OrderBooks.of(symbol)
                    .submitLimitOrder(symbol, side, req.price, req.quantity)

                ctx.json(mapOf("order" to order, "trades" to trades))
            } catch (e: IllegalArgumentException) {
                fail(ctx, 400, e)
            } catch (e: Exception) {
                fail(ctx, 500, e)
            }
        }

        // Recent trades: /api/v1/trades/:symbol?limit=50
        router.get("/api/v1/trades/:symbol").handler { ctx ->
            try {
                val symbol = ctx.pathParam("symbol")
                val limit = ctx.queryParam("limit").firstOrNull()?.toIntOrNull() ?: 50
                val trades = OrderBooks.of(symbol).getTrades(limit)
                ctx.json(trades)
            } catch (e: Exception) {
                fail(ctx, 400, e)
            }
        }

        // Global failure handler (fallback)
        router.route().failureHandler { ctx ->
            val status = ctx.statusCode().takeIf { it > 0 } ?: 500
            val failure = ctx.failure()
            val msg = failure?.message ?: "Unhandled error"
            ctx.response().setStatusCode(status).putHeader("content-type", "application/json")
                .end(io.vertx.core.json.Json.encode(ErrorResponse(msg)))
        }

        vertx.createHttpServer(
            HttpServerOptions().setPort(8080).setHost("0.0.0.0")
        ).requestHandler(router).listen { ar ->
            if (ar.succeeded()) {
                println("API listening on http://0.0.0.0:8080")
            } else {
                ar.cause().printStackTrace()
            }
        }
    }

    private fun fail(ctx: io.vertx.ext.web.RoutingContext, status: Int, e: Exception) {
        ctx.fail(status, e)
    }
}

fun main() {
    val vertx = Vertx.vertx()
    vertx.deployVerticle(ApiVerticle())
}
