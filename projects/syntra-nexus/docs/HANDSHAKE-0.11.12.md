# NEXUS durable replay ledger — 0.11.12-dev

## Purpose

The 0.11.11 replay guard kept sequence and nonce history in process memory. This milestone adds a durable store so a process restart does not reset the local replay window.

## Data model

Each inbound session stores a 22-character request ID, sender SHA-256 fingerprint, invitation expiry, canonical transcript SHA-256, last accepted sequence and SHA-256 hashes of accepted 32-byte nonces. Raw nonce values, private keys, QR invitation tokens and message payloads are not stored.

## Storage and commit behavior

- AES-256-GCM encryption with a fresh randomized 96-bit IV for every write.
- Key held by Android Keystore under a dedicated alias.
- Authenticated additional data binds ciphertext to the SYNTRA-NEXUS-REPLAY-v1 format context.
- Android AtomicFile writes and fd.sync() are used for replacement.
- The guard persists a candidate snapshot before changing in-memory state.
- If persistence fails, the frame is not accepted and PERSISTENCE_FAILURE is returned.
- Corrupt, unauthenticated, duplicate or malformed persisted state prevents initialization; it is never silently reset to empty.

## Bounds

- 1,024 sessions by default, hard ceiling 4,096.
- 256 accepted frames per session.
- Encrypted file ceiling: 20 MiB.
- Expired sessions are pruned only as part of a successfully committed accepted-frame update.

## Security boundary

This is durable replay state on one installation only. It does not coordinate state between devices, redeem QR tokens, make invitation redemption distributed or atomic, and is not connected to a socket or UI. A separate ledger is required on each peer. Android Keystore reset, app data clear, reinstall or device restore can make old local state unavailable; an existing encrypted ledger that cannot be decrypted must fail closed.

## Verification status

Unit tests cover restoration, duplicate replay after recreation, sequence continuity, nonce-history restoration, expired-session pruning, malformed records and fail-closed storage errors. They are authored but have not been executed here. Android Keystore and AtomicFile behavior still require CI and physical-device testing.
