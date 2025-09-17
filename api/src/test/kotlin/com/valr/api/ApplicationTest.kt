package com.valr.api

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import java.time.Clock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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

        val buyOrder = JsonObject().put("side", "BUY").put("price", "500000.00").put("quantity", "0.01")

        val (ts, sig) = sign("POST", "/api/orders/$symbol", buyOrder.encode())

        client.post(8080, "localhost", "/api/orders/$symbol")
                .putHeader("X-VALR-API-KEY", "test-key")
                .putHeader("X-VALR-TIMESTAMP", ts)
                .putHeader("X-VALR-SIGNATURE", sig)
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
        val (ts, sig) = sign("POST", "/api/orders/$symbol", badOrder.encode())
        client.post(8080, "localhost", "/api/orders/$symbol")
                .putHeader("X-VALR-API-KEY", "test-key")
                .putHeader("X-VALR-TIMESTAMP", ts)
                .putHeader("X-VALR-SIGNATURE", sig)
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
        val (ts, sig) = sign("POST", "/api/orders/$symbol", badOrder.encode())
        client.post(8080, "localhost", "/api/orders/$symbol")
                .putHeader("X-VALR-API-KEY", "test-key")
                .putHeader("X-VALR-TIMESTAMP", ts)
                .putHeader("X-VALR-SIGNATURE", sig)
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

        val (ts1, sig1) = sign("POST", "/api/orders/$symbol", mk("100").encode())
        val (ts2, sig2) = sign("POST", "/api/orders/$symbol", mk("200").encode())
        val (ts3, sig3) = sign("POST", "/api/orders/$symbol", mk("300").encode())

        client.post(8080, "localhost", "/api/orders/$symbol").putHeader("X-VALR-API-KEY", "test-key").putHeader("X-VALR-TIMESTAMP", ts1).putHeader("X-VALR-SIGNATURE", sig1).sendJsonObject(mk("100"))
                .compose { client.post(8080, "localhost", "/api/orders/$symbol").putHeader("X-VALR-API-KEY", "test-key").putHeader("X-VALR-TIMESTAMP", ts2).putHeader("X-VALR-SIGNATURE", sig2).sendJsonObject(mk("200")) }
                .compose { client.post(8080, "localhost", "/api/orders/$symbol").putHeader("X-VALR-API-KEY", "test-key").putHeader("X-VALR-TIMESTAMP", ts3).putHeader("X-VALR-SIGNATURE", sig3).sendJsonObject(mk("300")) }
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
        val (ts1, sig1) = sign("POST", "/api/orders/$symbol", sell.encode())
        val (ts2, sig2) = sign("POST", "/api/orders/$symbol", buy.encode())
        client.post(8080, "localhost", "/api/orders/$symbol").putHeader("X-VALR-API-KEY", "test-key").putHeader("X-VALR-TIMESTAMP", ts1).putHeader("X-VALR-SIGNATURE", sig1).sendJsonObject(sell)
                .compose { client.post(8080, "localhost", "/api/orders/$symbol").putHeader("X-VALR-API-KEY", "test-key").putHeader("X-VALR-TIMESTAMP", ts2).putHeader("X-VALR-SIGNATURE", sig2).sendJsonObject(buy) }
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
        val (ts, sig) = sign("POST", "/api/orders/$s1", buy.encode())
        client.post(8080, "localhost", "/api/orders/$s1").putHeader("X-VALR-API-KEY", "test-key").putHeader("X-VALR-TIMESTAMP", ts).putHeader("X-VALR-SIGNATURE", sig).sendJsonObject(buy)
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

    @Test
    fun missingAuthReturns403(testContext: VertxTestContext) {
        val symbol = "NOAUTH"
        val order = JsonObject().put("side", "BUY").put("price", "1").put("quantity", "1")
        client.post(8080, "localhost", "/api/orders/$symbol")
                // No auth headers on purpose
                .sendJsonObject(order)
                .onComplete(
                        testContext.succeeding<HttpResponse<Buffer>> { res ->
                            assertEquals(403, res.statusCode())
                            testContext.completeNow()
                        }
                )
    }

    @Test
    fun wrongContentTypeReturns403(testContext: VertxTestContext) {
        val symbol = "BADCT"
        val payload = Buffer.buffer("side=BUY&price=1&quantity=1")
        client.post(8080, "localhost", "/api/orders/$symbol")
                .putHeader("content-type", "text/plain")
                .sendBuffer(payload)
                .onComplete(
                        testContext.succeeding<HttpResponse<Buffer>> { res ->
                            assertEquals(403, res.statusCode())
                            testContext.completeNow()
                        }
                )
    }

    @Test
    fun iocDoesNotRestRemainder(testContext: VertxTestContext) {
        val symbol = "TSTIOC"
        val ask = JsonObject().put("side", "SELL").put("price", "100").put("quantity", "0.3")
        val buyIoc = JsonObject().put("side", "BUY").put("price", "100").put("quantity", "0.5").put("timeInForce", "IOC")

        val (ts1, sig1) = sign("POST", "/api/orders/$symbol", ask.encode())
        val (ts2, sig2) = sign("POST", "/api/orders/$symbol", buyIoc.encode())

        client.post(8080, "localhost", "/api/orders/$symbol").putHeader("X-VALR-API-KEY", "test-key").putHeader("X-VALR-TIMESTAMP", ts1).putHeader("X-VALR-SIGNATURE", sig1).sendJsonObject(ask)
                .compose { client.post(8080, "localhost", "/api/orders/$symbol").putHeader("X-VALR-API-KEY", "test-key").putHeader("X-VALR-TIMESTAMP", ts2).putHeader("X-VALR-SIGNATURE", sig2).sendJsonObject(buyIoc) }
                .compose { client.get(8080, "localhost", "/api/orderbook/$symbol").send() }
                .onComplete(
                        testContext.succeeding<HttpResponse<Buffer>> { res ->
                            assertEquals(200, res.statusCode())
                            val ob = res.bodyAsJsonObject()
                            val bids = ob.getJsonArray("bids")
                            // IOC remainder should not rest as bid
                            assertTrue(bids == null || bids.isEmpty)
                            testContext.completeNow()
                        }
                )
    }
}

// ---- Test helpers ----
private fun sign(verb: String, path: String, body: String = ""): Pair<String, String> {
    val secret = "test-secret"
    val ts = Clock.systemUTC().millis().toString()
    val mac = Mac.getInstance("HmacSHA512")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA512"))
    mac.update(ts.toByteArray())
    mac.update(verb.uppercase().toByteArray())
    mac.update(path.toByteArray())
    mac.update(body.toByteArray())
    val sig = mac.doFinal().joinToString(separator = "") { String.format("%02x", it) }
    return ts to sig
}
