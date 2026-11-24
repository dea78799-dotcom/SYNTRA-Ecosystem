# TLS pairing provenance enforcement — 0.11.29-dev

## Objective

A certificate fingerprint alone identifies a public key, but it does not prove that the peer is trusted under the specific pairing transaction currently being authorized. This milestone binds the TLS trust decision to the pairing request ID and transcript SHA-256 already stored with the trusted-device record.

## Changes

- `NexusTlsContextFactory.create` now requires:
  - expected peer fingerprint (64 lowercase hexadecimal characters)
  - expected pairing request ID (22 URL-safe characters)
  - expected transcript SHA-256 (64 lowercase hexadecimal characters)
  - optional local Android Keystore alias, defaulting to the installation identity
- Context creation checks the active registry record and calls `TrustedDeviceRegistry.isActiveForSession` before constructing the TLS context.
- `PinnedPeerTrustManager` repeats the exact provenance check when the peer certificate is validated. It checks the current registry, not a cached list, so a revoked record or re-paired identity cannot pass using stale context.
- `CapabilityRequestTlsIngress` checks the presented TLS certificate against the same transcript request ID and hash before it asks the consent coordinator to prepare a user-facing request.
- Legacy trusted-device records without pairing provenance remain visible in the registry but cannot authenticate a new TLS session.
- Added unit tests for exact match, different request ID, different transcript hash and legacy records.
- Added JSSE loopback handshake tests that reject a certificate with a matching public key but mismatched request ID or transcript hash.

## Security boundaries

This milestone does not create a production socket, advertise a LAN service, redeem QR tokens, transfer files, capture screens or perform remote-control actions. It only tightens the trust checks used by a TLS context and tests them with local loopback sockets.

The pairing request ID and transcript hash are supplied by the already-verified pairing flow. They are not accepted from an unauthenticated network packet as authoritative values. A future transport integration must obtain them from the locally verified pairing transaction and re-check the current trusted-device record at each authorization boundary.

## Verification

Source and tests have been committed, but this milestone remains unverified until GitHub Actions reports successful JVM unit tests, Android debug assembly and Android emulator instrumentation tests. A successful loopback test would not by itself establish production LAN readiness.
