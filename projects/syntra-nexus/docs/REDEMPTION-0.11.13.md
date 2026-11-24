# Pairing token redemption protocol — 0.11.13-dev

## Purpose

This milestone defines a bounded, versioned redemption request and issuer-side verifier for the existing one-time QR token registry. It does not open a socket or send a token.

## Request fields

- Fixed 22-character invitation request ID.
- Exact invitation expiry timestamp.
- Canonical 32-byte base64url token (43 characters).
- Receiver's canonical EC P-256 public identity key (1–512 bytes).

The request codec uses the SNR1 marker and protocol version 1. It rejects unknown versions, invalid marker, malformed token, invalid EC keys, length mismatch, truncation, trailing bytes and packets above the fixed maximum.

## Redemption checks

The issuer verifier compares the request ID, expiry and token against the exact invitation created by this installation. It validates the receiver's public key encoding and then calls the existing synchronized, persistent one-time token registry. The registry remains the authority for ACTIVE, REDEEMED, EXPIRED and REVOKED state; only one redemption can win.

A successful redemption consumes the token. If the pairing is interrupted afterward, the token stays consumed and the user must create a fresh QR invitation. This is intentional fail-closed behavior; a consumed bearer token is never reactivated after a disconnect.

## Critical transport boundary

The request contains the raw QR bearer token. It must only be transmitted after mutual TLS has completed and the remote peer certificate has been pinned to the expected device identity. Never send this payload over plain TCP, an unverified TLS session, a QR image, logs, analytics, or a public channel. Never use a trust-all certificate manager.

The codec and verifier are local protocol components only. They are not wired to a socket, UI, TLS session or trusted-device registration. A decoded receiver public key is only a claimed identity until the subsequent transcript-bound signature proof succeeds and both devices approve pairing.

## Verification status

Tests cover codec round trips, malformed packets, exact invitation binding, token mismatch, expiry, one-time redemption and peer-key validation. They are authored but not executed in this environment. CI and physical-device TLS verification remain required.
