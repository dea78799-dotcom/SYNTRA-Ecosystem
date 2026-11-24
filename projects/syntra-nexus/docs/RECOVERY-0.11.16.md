# Pairing Recovery & Integrity Tests — 0.11.16-dev

## Scope

This milestone strengthens the local pairing-transaction recovery contract with JVM unit tests. It does not add sockets, TLS handshakes, token transmission, or automatic pairing.

## Recovery behavior covered

- A restored non-terminal transaction is downgraded to `RECOVERY_REQUIRED` and the downgraded state must be persisted before the coordinator is exposed.
- A restored `PAIRED` snapshot is also downgraded. A prior process's state is not treated as fresh proof of current consent.
- Reopening an already `RECOVERY_REQUIRED` snapshot is idempotent.
- A restored terminal `ABORTED` snapshot remains terminal.
- Invalid persisted identity fields reject coordinator initialization.
- If saving the recovery downgrade fails, coordinator construction fails closed.
- Explicitly discarding a recovery snapshot clears durable storage before clearing in-memory state; a clear failure preserves the snapshot.
- Expiry persistence failure leaves the prior in-memory state unchanged.

## Verification boundary

The expanded coordinator test suite uses a deterministic in-memory persistence adapter. It tests coordinator behavior and injected storage failures, not Android filesystem crash consistency or Android Keystore hardware/software behavior.

The encrypted adapter still requires physical-device checks for:
- Android Keystore key loss or invalidation.
- Authentication failure after ciphertext modification.
- AtomicFile recovery after process termination during replacement.
- App restart with valid and invalid persisted ciphertext.

No successful Gradle build, unit-test run, APK, or physical-device verification is claimed until GitHub Actions or a local Android build reports an actual result.

## Security invariants retained

- Pairing transaction persistence contains no QR bearer token, private key, signature, raw nonce, or message payload.
- A redeemed QR token is not restored or reactivated.
- Recovery cannot resume an interrupted handshake. The user must discard the local recovery record and create a fresh invitation.
- No network socket or remote action is enabled by this test-only milestone.
