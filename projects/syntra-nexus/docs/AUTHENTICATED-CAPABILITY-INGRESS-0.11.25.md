# Authenticated capability request ingress — 0.11.25-dev

## Scope

- Add `CapabilityRequestTlsIngress`, an admission gate for an already-established TLS session.
- Require a valid TLS session using TLS 1.2 or TLS 1.3.
- Require a local X.509 certificate to be present, so a one-way TLS session is rejected.
- Require a peer X.509 certificate chain with a bounded length, a valid leaf certificate, a non-CA leaf, and a public key that matches the active trusted-device registry and the expected peer fingerprint.
- Require the request's peer fingerprint to match the expected fingerprint.
- Only after these checks delegate to `CapabilityConsentCoordinator.prepare()`, which verifies the transcript, pairing session, transaction, request freshness and peer's role-bound signature.
- Add unit tests that exercise the production TLS protocol allowlist.

## Security boundary

This is an ingress policy adapter, not a network implementation. It does not create sockets, configure an SSLSocket/SSLEngine, perform a handshake, read bytes from a connection, or transmit any data. The caller must pass the SSLSession from a completed connection configured with the NEXUS TLS context and pinned trust manager.

The adapter checks that a local certificate is present, but a real deployment still needs an end-to-end mutual-TLS integration test proving that the peer certificate was validated by the pinned trust manager and that the local certificate was actually requested and accepted. Do not treat arbitrary caller-supplied SSLSession instances as authenticated channel evidence.

No file transfer, screen capture, usage-statistics export, or remote-control action is enabled.

## Verification

Repository structure checks and Android build/unit/instrumentation jobs must complete before release. This document does not claim a successful APK build or device test.
