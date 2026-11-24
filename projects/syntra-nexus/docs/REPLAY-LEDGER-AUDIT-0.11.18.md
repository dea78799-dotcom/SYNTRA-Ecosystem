# SYNTRA NEXUS — Durable Replay Ledger Audit 0.11.18-dev

## Scope

This audit is limited to local replay-ledger persistence. It does not enable network transport, token redemption over a socket, screen sharing or remote device actions.

## Finding: AtomicFile backup recovery was bypassed

The previous `load()` implementation checked `baseFile.exists()` and returned an empty list before calling `AtomicFile.openRead()`. During an interrupted replacement, Android AtomicFile may hold the last committed snapshot only in `baseFile.bak`. Returning an empty list in that condition would silently discard the replay history.

### Correction

- Call `openRead()` first. AtomicFile then gets the opportunity to restore its backup before the content is parsed.
- Treat `FileNotFoundException` as an empty store only when AtomicFile has no committed base or backup to recover.
- Preserve all other read, size, decryption and parsing failures as errors; never reinterpret corruption as an empty ledger.
- Bound the streaming read to 20 MiB before allocating unbounded memory.
- Serialize all load/save operations across adapter instances with a shared process-level lock.
- Validate all replay records on both save and restore, including unique session keys and unique nonce hashes.

## Tests added

Android instrumentation tests cover:
1. Encrypted replay state survives persistence object recreation.
2. A backup-only store is restored when the base file is missing.
3. A committed backup replaces a partial base file after interrupted replacement.
4. Ciphertext tampering fails authentication and preserves the file.
5. A missing Android Keystore key does not trigger replacement-key creation or erase the encrypted ledger.

## Security boundaries

- The replay ledger remains local to this Android installation.
- It is not distributed between peers and is not yet wired to any socket.
- A successful local storage test does not establish mutual TLS or prove remote peer identity.
- No QR bearer token is sent, no pairing is performed and no remote capability is enabled by this change.

## Verification status

Source and instrumentation tests have been committed. A successful Gradle build, JVM test run or Android emulator test run is **not claimed** until GitHub Actions reports an explicit successful result.
