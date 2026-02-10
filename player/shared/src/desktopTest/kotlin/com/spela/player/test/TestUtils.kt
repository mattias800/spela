package com.spela.player.test

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode

/** MockEngine factory with a no-op handler. Use in tests where real HTTP calls are not needed. */
val NoOpMockEngineFactory = object : HttpClientEngineFactory<MockEngineConfig> {
    override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine {
        return MockEngine(MockEngineConfig().apply {
            addHandler { respond("", HttpStatusCode.OK) }
            block()
        })
    }
}
