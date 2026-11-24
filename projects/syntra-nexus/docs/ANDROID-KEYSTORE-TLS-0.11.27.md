# Android Keystore mutual-TLS identity verification — 0.11.27-dev

## Scope

This milestone adds Android instrumentation tests for the existing Android Keystore-backed TLS key manager and context factory. It does not open sockets, start a listener, perform a TLS handshake, or enable LAN traffic.

## Tests

1. Confirm the TLS key manager reads the P-256 identity certificate from the Android Keystore and returns the expected client/server aliases.
2. Confirm the Android Keystore private key has no exportable encoding or format and can produce a signature verifiable by the associated certificate public key.
3. Confirm the TLS context factory accepts an active pinned peer and configures only TLS 1.2/1.3 on a client socket object that is not connected.
4. Confirm unknown and revoked peers are rejected before a context is created.
5. Confirm the TLS key manager fails closed when the local device identity is missing.

## Security boundary

The tests use the production identity alias through DeviceIdentityStore and delete that test identity before and after each test. A software-generated P-256 public key is used only as an in-memory trusted peer record. No private test key is placed in app assets or production source.

These tests do not establish that Android's TLS provider completes a real mutual-authentication handshake using the Android Keystore key. A dedicated emulator loopback handshake test remains a separate milestone after these identity-manager assertions are green.

## Verification

GitHub source readback confirms that files are present; it does not prove the instrumentation suite or APK build passed. The CI emulator job must complete successfully before this milestone can be called verified.
