package com.valr.api

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationTest {

    private lateinit var vertx: Vertx
    private lateinit var client: WebClient

    @BeforeAll
    fun deployVerticle(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        client = WebClient.create(vertx)
        vertx.deployVerticle(
                ApiVerticle(),
                DeploymentOptions(),
                testContext.succeedingThenComplete()
        )
    }

    @AfterAll
    fun tearDown() {
        vertx.close()
    }

    @Test
    fun healthCheck(testContext: VertxTestContext) {
        client.get(8080, "localhost", "/healthz")
                .send()
                .onComplete(
                        testContext.succeeding<HttpResponse<Buffer>> { res ->
                            assertEquals(200, res.statusCode())
                            assertEquals("ok", res.bodyAsString())
                            testContext.completeNow()
                        }
                )
    }

    @Test
    fun orderLifecycle(testContext: VertxTestContext) {
        val symbol = "TSTLIFE"

        val buyOrder =
                JsonObject().put("side", "BUY").put("price", "500000.00").put("quantity", "0.01")

        client.post(8080, "localhost", "/api/orders/$symbol")
                .sendJsonObject(buyOrder)
                .onComplete(
                        testContext.succeeding<HttpResponse<Buffer>> { res ->
                            assertEquals(200, res.statusCode())
                            val body = res.bodyAsJsonObject()
                            assertTrue(body.containsKey("order"))
                            assertEquals("0.01", body.getJsonObject("order").getString("remaining"))

                            client.get(8080, "localhost", "/api/orderbook/$symbol")
                                    .send()
                                    .onComplete(
                                            testContext.succeeding<HttpResponse<Buffer>> { snap ->
                                                assertEquals(200, snap.statusCode())
                                                val ob = snap.bodyAsJsonObject()
                                                val bids = ob.getJsonArray("bids")
                                                assertTrue(bids != null && !bids.isEmpty)
                                                testContext.completeNow()
                                            }
                                    )
                        }
                )
    }

    @Test
    fun invalidSideReturns400(testContext: VertxTestContext) {
        val symbol = "TSTBAD"
        val badOrder = JsonObject().put("side", "BUYX").put("price", "1").put("quantity", "1")
        client.post(8080, "localhost", "/api/orders/$symbol")
                .sendJsonObject(badOrder)
                .onComplete(
                        testContext.succeeding<HttpResponse<Buffer>> { res ->
                            Assertions.assertEquals(400, res.statusCode())
                            val err = res.bodyAsJsonObject()
                            assertTrue(err.containsKey("error"))
                            testContext.completeNow()
                        }
                )
    }

    @Test
    fun missingFieldsReturnError(testContext: VertxTestContext) {
        val symbol = "TSTMISS"
        val badOrder = JsonObject().put("side", "BUY").put("price", "1") // missing quantity
        client.post(8080, "localhost", "/api/orders/$symbol")
                .sendJsonObject(badOrder)
                .onComplete(
                        testContext.succeeding<HttpResponse<Buffer>> { res ->
                            Assertions.assertTrue(res.statusCode() in 400..599)
                            testContext.completeNow()
                        }
                )
    }

    @Test
    fun orderbookDepthQuery(testContext: VertxTestContext) {
        val symbol = "TSTDEPTH"
        val mk = { p: String -> JsonObject().put("side", "BUY").put("price", p).put("quantity", "0.01") }

        client.post(8080, "localhost", "/api/orders/$symbol").sendJsonObject(mk("100"))
                .compose { client.post(8080, "localhost", "/api/orders/$symbol").sendJsonObject(mk("200")) }
                .compose { client.post(8080, "localhost", "/api/orders/$symbol").sendJsonObject(mk("300")) }
                .compose { client.get(8080, "localhost", "/api/orderbook/$symbol?depth=2").send() }
                .onComplete(
                        testContext.succeeding<HttpResponse<Buffer>> { res ->
                            assertEquals(200, res.statusCode())
                            val ob = res.bodyAsJsonObject()
                            val bids = ob.getJsonArray("bids")
                            assertEquals(2, bids.size())
                            testContext.completeNow()
                        }
                )
    }

    @Test
    fun tradesEndpointRespectsLimit(testContext: VertxTestContext) {
        val symbol = "TSTTRADES"
        val sell = JsonObject().put("side", "SELL").put("price", "10").put("quantity", "1")
        val buy = JsonObject().put("side", "BUY").put("price", "10").put("quantity", "1")
        client.post(8080, "localhost", "/api/orders/$symbol").sendJsonObject(sell)
                .compose { client.post(8080, "localhost", "/api/orders/$symbol").sendJsonObject(buy) }
                .compose { client.get(8080, "localhost", "/api/trades/$symbol?limit=1").send() }
                .onComplete(
                        testContext.succeeding<HttpResponse<Buffer>> { res ->
                            assertEquals(200, res.statusCode())
                            val arr = res.bodyAsJsonArray()
                            assertEquals(1, arr.size())
                            testContext.completeNow()
                        }
                )
    }

    @Test
    fun symbolIsolation(testContext: VertxTestContext) {
        val s1 = "AAAUSD"
        val s2 = "BBBUSD"
        val buy = JsonObject().put("side", "BUY").put("price", "100").put("quantity", "1")
        client.post(8080, "localhost", "/api/orders/$s1").sendJsonObject(buy)
                .compose { client.get(8080, "localhost", "/api/orderbook/$s2").send() }
                .onComplete(
                        testContext.succeeding<HttpResponse<Buffer>> { res ->
                            assertEquals(200, res.statusCode())
                            val ob = res.bodyAsJsonObject()
                            val bids = ob.getJsonArray("bids")
                            val asks = ob.getJsonArray("asks")
                            assertTrue(bids == null || bids.isEmpty)
                            assertTrue(asks == null || asks.isEmpty)
                            testContext.completeNow()
                        }
                )
    }
}
