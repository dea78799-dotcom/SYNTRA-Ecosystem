# SYNTRA NEXUS — Encrypted AtomicFile Recovery Audit 0.11.18-dev

## Scope

This audit covers the four Android Keystore-backed AtomicFile stores:
- Encrypted handshake replay ledger
- Encrypted trusted-device registry
- Encrypted one-time pairing-token registry
- Encrypted pairing transaction

It does not enable network transport, token redemption over a socket, screen sharing or remote device actions.

## Findings

### 1. Backup recovery could be bypassed

Three adapters checked `baseFile.exists()` and returned an empty store before calling `AtomicFile.openRead()`. During an interrupted replacement, the last committed snapshot may exist only in `.bak`. Treating that state as empty could discard trusted-device, token or replay history.

**Correction:** All stores now call `openRead()` first. Only a genuine `FileNotFoundException` after AtomicFile's recovery attempt is treated as an absent store. Other I/O, size, decryption and parsing failures remain errors.

### 2. AtomicFile operations needed serialization

AtomicFile requires callers to coordinate access. The replay, trusted-device and token adapters now use a shared process-level lock for load/save operations. The transaction adapter already serializes load/save/clear operations.

### 3. Missing Keystore keys must not be silently replaced

If ciphertext exists but its AES key is missing, generating a new key cannot recover the old ciphertext. The replay, trusted-device and token adapters now fail closed in this condition. The transaction adapter's check also includes the `.bak` path, not just the base file.

### 4. Encrypted reads and stored records needed explicit bounds

The replay, trusted-device and token adapters now read encrypted data with fixed maximum sizes and validate stored record counts and fields before returning records. The replay ledger additionally checks sequence bounds, nonce-hash count/format/uniqueness and duplicate session keys.

## Tests added

- Replay storage: persistence recreation, backup-only recovery, partial-replacement recovery, ciphertext tampering, missing key with base file, and missing key with backup-only file.
- Trusted-device and token stores: backup-only recovery, missing-key fail-closed behavior, and token-state validation.
- Existing transaction storage tests continue to cover persistence recreation, ciphertext tampering, missing key, and AtomicFile backup recovery; a backup-only missing-key case was added.

## Security boundaries

- These are local persistence protections, not distributed replay protection.
- The replay ledger is not connected to a socket and does not coordinate state between devices.
- A stored trusted-device identity must still originate from a transcript-bound handshake with both approvals.
- A valid QR scan or LAN discovery record is not proof of identity or consent.
- No QR bearer token is sent, no pairing is performed and no remote capability is enabled by this change.

## Verification status

Source and Android instrumentation tests have been committed. A successful Gradle build, JVM test run or Android emulator test run is **not claimed** until GitHub Actions reports an explicit successful result.
