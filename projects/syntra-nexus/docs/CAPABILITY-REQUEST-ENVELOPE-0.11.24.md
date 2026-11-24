# Capability request envelope — 0.11.24-dev

## Added

- Add a versioned `SNCQ` binary envelope for capability requests plus the peer's approval signature.
- Bound the full envelope to 1,024 bytes and each UTF-8 field to 128 bytes.
- Validate marker, protocol version, capability name, request fields, timestamp constraints, signature length, truncation and trailing bytes.
- Decode UTF-8 strictly; malformed and unmappable byte sequences are rejected.
- Defensively copy signature bytes at construction and access boundaries.
- Add JVM tests for round-trip behavior, defensive copies, wrong marker, unsupported version, unknown capability, truncation, trailing bytes and oversized input.

## Security boundary

- Decoding does not establish trust and does not verify the signature.
- A caller must receive the envelope only inside a future pinned mutual-TLS session, verify the peer signature, and bind request ID, transcript hash, peer fingerprint, session and current trusted-device authorization before calling `CapabilityConsentCoordinator.prepare()`.
- No socket, listener, network request handler, QR token exchange, file transfer, screen capture or remote action is enabled.
- The codec is not yet connected to the consent UI or transport.

## Verification

Tests are committed but are not claimed to pass until GitHub Actions reports success. No APK build or device instrumentation result is claimed by this document.
