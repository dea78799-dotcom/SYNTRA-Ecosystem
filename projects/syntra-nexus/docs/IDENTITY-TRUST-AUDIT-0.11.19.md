# SYNTRA NEXUS — Identity & Trust Audit 0.11.19-dev

## Scope

Review the local identity, transcript key encoding, signature binding, trusted-device registry, revocation and pinned peer identity policy. No live network connection or data transfer is enabled.

## Findings and corrections

### 1. Transcript key material was not constrained to P-256

Previously, transcript construction only checked that public-key bytes were non-empty and below a size limit. This allowed unsupported curves or malformed key material to be placed in a transcript and rejected only later in signature verification.

**Correction:** `PairingIdentityCrypto.requireP256PublicKey` parses canonical X.509 SubjectPublicKeyInfo, requires EC, compares the full curve parameters against `secp256r1`, and rejects non-canonical encodings. Pairing transcript construction uses this check before hashing the transcript.

### 2. Android Keystore identity needed a key-pair consistency check

A present alias and certificate alone do not prove the private key is usable or matches the certificate public key.

**Correction:** On identity load and immediately after key generation, the app validates the P-256 public key and EC private-key algorithm, signs a fresh random challenge with the private key, and verifies it using the public key. A mismatch fails closed. Identity public-key bytes are returned as defensive copies.

### 3. Local proof signing was not bound to a transcript object

The previous identity API accepted any 32-byte hash and a caller-supplied role. That made it too easy for future code to request a signature without first checking that the local public key occupied that role in the transcript.

**Correction:** The API now accepts a `PairingTranscript`, checks that the local Keystore public key exactly matches the public key assigned to the requested role, and signs that transcript's hash. Approval signing already performed the same role/key check.

### 4. Trusted-device registry needed curve and chronology validation

The registry previously verified the stored fingerprint against the bytes, but did not itself enforce the required curve. Revocation also accepted a timestamp earlier than the original trust timestamp, creating a record that would fail validation when loaded again.

**Correction:** Restored trusted-device keys must be canonical P-256 keys. Revocation time must be greater than or equal to the trust time. Persistence still commits before in-memory mutation.

### 5. Peer pinning and local TLS identity needed defense in depth

The peer pinning helper previously accepted any canonical EC curve if a caller supplied a matching record. The TLS key manager also checked EC but did not explicitly require P-256.

**Correction:** Peer pinning and local TLS identity initialization now require canonical P-256 keys. The existing active-record check still rejects revoked or unknown peers and optionally pins one exact expected fingerprint.

## Test coverage added

- Identity crypto: reject secp384r1 and RSA public keys.
- Transcript: reject secp384r1 and RSA identities before transcript creation.
- Trusted-device registry: reject revocation timestamps earlier than trust.
- Peer pinning: reject non-P-256 EC keys even if a record is supplied.
- Android Keystore instrumentation: stable identity across store instances, defensive public-key copies, transcript-role-bound signatures, and explicit identity deletion/replacement.

## Existing security properties retained

- ECDSA/SHA-256 signatures are bound to a domain separator, signer role and transcript hash.
- The handshake state machine accepts a peer proof only for the responder role and the exact transcript pinned to the session.
- A separate signed APPROVE decision is required after local approval.
- The only paired state is PAIRED with BOTH_APPROVED.
- Trusted-device registration requires the exact transcript and both approvals.
- Revoked devices are excluded from active pinning decisions.

## Important boundaries

- This audit does not prove that a remote peer is physically the owner of a device; out-of-band fingerprint comparison remains useful for first-time enrollment.
- The local trusted-device registry is not yet connected to a completed pairing transport.
- Local revocation does not notify a remote device or terminate an existing remote session.
- The pinned trust manager is not a general CA/hostname validation system. It is an identity-pinning component for a future protocol and must be paired with a reviewed certificate and TLS policy.
- No LAN socket is opened, no QR bearer token is transmitted, no pairing is performed and no remote capability is enabled.

## Verification status

The source changes and tests are committed. A successful Gradle build, JVM test run or Android emulator run is not claimed until GitHub Actions reports an explicit successful result.
