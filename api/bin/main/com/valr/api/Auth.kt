package com.valr.api

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import java.time.Clock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Simple in-memory API key store (dev-only). */
object ApiKeys {
    private val keys: MutableMap<String, String> = mutableMapOf()

    init {
        // Load from env if present; otherwise seed a test key for local/dev usage.
        val envKey = System.getenv("API_KEY")
        val envSecret = System.getenv("API_SECRET")
        if (!envKey.isNullOrBlank() && !envSecret.isNullOrBlank()) {
            keys[envKey] = envSecret
        } else {
            keys["test-key"] = "test-secret"
        }
    }

    fun secretFor(key: String): String? = keys[key]
}

/** HMAC (SHA-512) signer compatible with VALR-style concatenation. */
object HmacSigner {
    fun sign(secret: String, timestamp: String, verb: String, path: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA512"))
        mac.update(timestamp.toByteArray())
        mac.update(verb.uppercase().toByteArray())
        mac.update(path.toByteArray())
        mac.update(body.toByteArray())
        val digest = mac.doFinal()
        return digest.toHexLower()
    }

    private fun ByteArray.toHexLower(): String {
        val sb = StringBuilder(this.size * 2)
        for (b in this) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}

/**
 * Vert.x handler enforcing simple VALR-like HMAC auth for mutating requests.
 * Requires headers: X-VALR-API-KEY, X-VALR-TIMESTAMP, X-VALR-SIGNATURE.
 * Also enforces content-type application/json for POST/PUT/PATCH.
 */
class HmacAuthHandler(private val clock: Clock = Clock.systemUTC()) : Handler<RoutingContext> {
    override fun handle(ctx: RoutingContext) {
        val method = ctx.request().method()
        // For mutating requests enforce JSON content-type
        if (method.name() == "POST" || method.name() == "PUT" || method.name() == "PATCH") {
            val ct = ctx.request().getHeader("content-type") ?: ""
            if (!ct.lowercase().contains("application/json")) {
                ctx.response()
                        .setStatusCode(403)
                        .putHeader("content-type", "application/json")
                        .end(io.vertx.core.json.Json.encode(ErrorResponse("content-type must be application/json")))
                return
            }
        }

        val apiKey = ctx.request().getHeader("X-VALR-API-KEY")
        val timestamp = ctx.request().getHeader("X-VALR-TIMESTAMP")
        val providedSig = ctx.request().getHeader("X-VALR-SIGNATURE")

        if (apiKey.isNullOrBlank() || timestamp.isNullOrBlank() || providedSig.isNullOrBlank()) {
            ctx.response()
                    .setStatusCode(403)
                    .putHeader("content-type", "application/json")
                    .end(io.vertx.core.json.Json.encode(ErrorResponse("Missing API authentication headers")))
            return
        }

        val secret = ApiKeys.secretFor(apiKey)
        if (secret == null) {
            ctx.response()
                    .setStatusCode(403)
                    .putHeader("content-type", "application/json")
                    .end(io.vertx.core.json.Json.encode(ErrorResponse("Invalid API key")))
            return
        }

        val verb = method.name()
        val path = ctx.request().path() // exclude host and query
        val body = ctx.body().asString() ?: ""
        val expected = HmacSigner.sign(secret, timestamp, verb, path, body)

        if (!expected.equals(providedSig, ignoreCase = true)) {
            ctx.response()
                    .setStatusCode(403)
                    .putHeader("content-type", "application/json")
                    .end(io.vertx.core.json.Json.encode(ErrorResponse("Invalid signature")))
            return
        }

        // Optionally, enforce timestamp freshness (skipped for simplicity in this assessment)
        ctx.next()
    }
}

