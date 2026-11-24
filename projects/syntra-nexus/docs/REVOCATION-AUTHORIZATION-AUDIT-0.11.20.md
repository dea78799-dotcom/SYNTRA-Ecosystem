# Revocation & Authorization Binding Audit — 0.11.20-dev

## Scope

This milestone hardens local authorization decisions after pairing. It does not add sockets, remote control, screen streaming, or a network capability protocol.

## Changes

- Trusted-device records now retain the exact pairing request ID and transcript SHA-256 that established trust.
- Encrypted trusted-device persistence stores those provenance fields. Older encrypted records without them remain readable and visible, but are not eligible to authorize a new session until the device is explicitly paired again.
- The registry exposes a synchronized current-state check that requires an active, non-revoked record, exact public key/fingerprint, request ID, and transcript hash.
- A new authorization policy requires all of the following at the point a new session is authorized: unexpired transcript, verifier-produced PAIRED/BOTH_APPROVED handshake, exact matching PAIRED transaction snapshot, and current registry provenance.
- Re-pairing the same public key replaces the record's provenance. A stale session and old transaction cannot regain authorization merely because the same key is active again.
- The local-approval transition now checks request ID, expiry, transcript hash, and responder fingerprint against the active transaction, not only the transcript hash.

## Security behavior

- Revocation is checked against the live in-memory registry on each authorization call; a snapshot taken before revocation is not sufficient.
- Persistence writes still occur before in-memory mutation. If encrypted persistence fails, revocation does not update the registry and the operation must be treated as failed.
- The authorization helper is not yet wired to TLS sockets or remote capability execution. Future transport code must call it immediately before accepting each new authenticated session and must separately bind every capability grant to that session.

## Compatibility

Legacy trusted-device records lacking pairing provenance can still be listed and revoked. They intentionally fail the new session-authorization check until re-paired through a fresh transcript.

## Verification

Five focused JVM tests are added for exact provenance authorization, revocation after an old snapshot, same-key re-pairing, transaction substitution, and expiry/incomplete pairing. Tests are authored but not claimed as passing until CI reports success. Android encrypted-persistence instrumentation and physical-device behavior remain to be verified.
