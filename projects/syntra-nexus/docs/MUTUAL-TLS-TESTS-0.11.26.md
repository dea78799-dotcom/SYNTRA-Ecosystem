# Mutual TLS loopback integration tests — 0.11.26-dev

## Scope

This milestone adds real JSSE mutual-TLS integration tests on loopback sockets only. It does not add a production listener, LAN discovery transport, remote pairing transport, or capability-data channel.

The tests use isolated EC P-256 test identities stored only under app/src/test/resources/tls/. Their private keys are fixtures for JVM tests and must never be copied into application assets, Android Keystore aliases, release artifacts, or production configuration.

## Covered cases

- Successful TLS handshake and a one-byte request/response when both peers present active pinned identities.
- Rejection of a client certificate whose key is not in the server's active trusted-device registry.
- Rejection when the client's previously trusted identity has been revoked.
- Rejection when the client does not present a certificate; the server socket requires client authentication.
- Rejection of an expired server certificate even when its public key fingerprint is otherwise pinned.
- TLS protocol selection remains limited to TLS 1.3 and TLS 1.2 by the production socket policy.

## Test boundary

All sockets bind to the loopback interface and use an ephemeral port. The tests exchange one fixed byte solely to prove that the authenticated channel completed. No external host, device, file, screen content, usage data, or remote-control operation is involved.

These tests exercise generic JSSE SSLContext, SSLServerSocket, and SSLSocket with the production PinnedPeerTrustManager and NexusTlsProtocolPolicy. They do not instantiate NexusTlsContextFactory, because that factory intentionally requires the real Android Keystore identity. Android Keystore-backed end-to-end tests remain a separate requirement.

## Verification status

The source and fixtures are committed for CI execution. A commit or repository readback is not proof that tests passed. Release remains blocked until the JVM test job, Android instrumentation job, and debug APK assembly all report success.
