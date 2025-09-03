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
        val symbol = "BTCZAR"

        val buyOrder =
                JsonObject().put("side", "BUY").put("price", "500000.00").put("quantity", "0.01")

        client.post(8080, "localhost", "/api/v1/orders/$symbol")
                .sendJsonObject(buyOrder)
                .onComplete(
                        testContext.succeeding<HttpResponse<Buffer>> { res ->
                            assertEquals(200, res.statusCode())
                            val body = res.bodyAsJsonObject()
                            assertTrue(body.containsKey("order"))
                            assertEquals("0.01", body.getJsonObject("order").getString("remaining"))

                            client.get(8080, "localhost", "/api/v1/orderbook/$symbol")
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
}
