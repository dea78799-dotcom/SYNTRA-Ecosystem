package com.syntra.nexus

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Validates the structure and lifetime of a scanned NEXUS invitation.
 *
 * Parsing is not redemption: this class does not authenticate a sender,
 * consume a token, prevent cross-device replay, or establish device trust.
 */
data class ParsedPairingInvitation(
    val requestId: String,
    val token: String,
    val expiresAtMillis: Long
)

object PairingPayloadParser {
    private const val SCHEME = "syntra-nexus"
    private const val HOST = "pair"
    private const val MAX_FUTURE_LIFETIME_MILLIS = PairingRequest.DEFAULT_TTL_MILLIS
    private val idPattern = Regex("^[A-Za-z0-9_-]{22}$")
    private val tokenPattern = Regex("^[A-Za-z0-9_-]{43}$")

    fun parse(
        payload: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ParsedPairingInvitation {
        require(payload.length <= 512) { "Invitation payload is too large." }
        val uri = try {
            URI(payload)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invitation URI is malformed.")
        }

        require(uri.scheme == SCHEME && uri.host == HOST && uri.rawPath.isNullOrEmpty()) {
            "Invitation address is not supported."
        }
        require(uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null) {
            "Invitation address contains unsupported components."
        }

        val parameters = parseQuery(uri.rawQuery ?: "")
        require(parameters.keys == setOf("v", "id", "token", "exp")) {
            "Invitation fields are incomplete or unexpected."
        }
        require(parameters["v"] == "1") { "Invitation version is not supported." }

        val requestId = parameters.getValue("id")
        val token = parameters.getValue("token")
        require(idPattern.matches(requestId)) { "Invitation ID is invalid." }
        require(tokenPattern.matches(token)) { "Invitation token is invalid." }

        val expiresAt = parameters.getValue("exp").toLongOrNull()
            ?: throw IllegalArgumentException("Invitation expiry is invalid.")
        require(expiresAt > nowMillis) { "Invitation has expired." }
        require(expiresAt - nowMillis <= MAX_FUTURE_LIFETIME_MILLIS) {
            "Invitation expiry exceeds the allowed lifetime."
        }

        return ParsedPairingInvitation(
            requestId = requestId,
            token = token,
            expiresAtMillis = expiresAt
        )
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        require(rawQuery.isNotBlank()) { "Invitation query is missing." }
        val result = linkedMapOf<String, String>()
        rawQuery.split("&").forEach { part ->
            val separator = part.indexOf('=')
            require(separator > 0) { "Invitation query is malformed." }
            val key = decode(part.substring(0, separator))
            val value = decode(part.substring(separator + 1))
            require(key !in result) { "Invitation contains duplicate fields." }
            result[key] = value
        }
        return result
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        throw IllegalArgumentException("Invitation contains invalid encoding.")
    }
}
