package com.valr.app

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplicationTest {

    @Test
    fun healthz_should_respond_ok() = testApplication {
        application { module() }
        val res: HttpResponse = client.get("/healthz")

        assertEquals(200, res.status.value)
        assertEquals("ok", res.bodyAsText())
    }
}
