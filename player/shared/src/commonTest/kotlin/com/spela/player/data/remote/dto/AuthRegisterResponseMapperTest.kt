package com.spela.player.data.remote.dto

import com.spela.client.models.AuthRegisterResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthRegisterResponseMapperTest {
    @Test
    fun pendingRegistrationResponseDecodesWithoutTokens() {
        val dto = Json.decodeFromString<AuthRegisterResponse>(
            """{"pending":true,"message":"Your account is pending admin approval."}""",
        )

        val error = assertFailsWith<IllegalStateException> {
            dto.toDomain()
        }
        assertEquals("Your account is pending admin approval.", error.message)
    }

    @Test
    fun successfulRegistrationResponseMapsTokensWhenPendingFieldsAreOmitted() {
        val dto = Json.decodeFromString<AuthRegisterResponse>(
            """{"accessToken":"access","refreshToken":"refresh"}""",
        )

        val tokens = dto.toDomain()

        assertEquals("access", tokens.accessToken)
        assertEquals("refresh", tokens.refreshToken)
    }
}
