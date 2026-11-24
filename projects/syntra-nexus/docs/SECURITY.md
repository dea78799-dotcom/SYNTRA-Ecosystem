# SYNTRA NEXUS — Security Baseline

## Required safeguards

- Pairing is visible and requires approval on both devices.
- QR invitations use cryptographically secure random values and expire after two minutes.
- QR tokens must be single-use, rate-limited and invalidated after successful redemption.
- The receiver must validate payload version, field lengths and expiry before attempting a connection.
- Both devices must authenticate peer identity; IP address and service discovery are not authentication.
- Each device receives only the minimum capabilities needed.
- The owner can inspect and revoke every pairing.
- No hidden collection of messages, photos, screen content, passwords, or app activity.
- No bypass of Android sandboxing, permissions, or platform restrictions.
- Network sessions must authenticate the peer and use modern TLS.
- Long-lived private keys must be generated and protected with Android Keystore where supported.
- Logs must not contain tokens, private keys, message content, or sensitive personal data.
- Sample devices and simulated states must be clearly labeled as demo data.

## Device identity requirements

- Generate a unique EC P-256 signing key per installation inside Android Keystore.
- Keep private key bytes non-exportable; exchange public keys only.
- Use SHA-256 fingerprints for human comparison, not as a substitute for proof of possession.
- Bind signatures to a canonical, versioned transcript containing both peer identities, both fresh nonces, the invitation request ID and expiry. Encode fields in a fixed order with explicit lengths; bind each signature to the signer's initiator/responder role.
- Use a NEXUS-specific domain separator and verify signatures against the exact public key being approved. Reject transcript reuse across roles, malformed field lengths, identical peer identities, and identical peer nonces.
- Never accept a peer solely because it supplied a fingerprint or was discovered on the LAN.
- Local key deletion must be clearly described as local identity reset; remote peers need a separate revocation/update mechanism.

## Threats to address before LAN pairing ships

- QR code photographed or presented to an unintended party.
- Replay of an expired or already redeemed invitation.
- Rogue device advertising a forged discovery record.
- Local network interception or downgrade.
- Unauthorized commands from a paired but restricted device.
- Lost or shared phones retaining trusted-device access.
- Repeated invalid redemption attempts causing resource exhaustion.

## Release gate

Do not label a build as a functional remote controller until QR scanning, one-time redemption, mutual authentication, explicit approval on both devices, capability enforcement, revocation, and failure cases have been tested on real devices.

## Additional monitoring and filtering safeguards

- Usage summaries use Android's explicit Usage Access grant and show data freshness; revoked access must stop reporting.
- Screen-sharing sessions use MediaProjection user consent, a foreground notification, an in-app indicator and stop controls. Do not silently start, capture input, or record by default.
- Website filtering is transparent, user-configured and locally processed. A VPN/DNS filter cannot inspect encrypted page content and must not claim perfect blocking.
- Do not collect or upload browsing history, message contents, keystrokes, screen recordings or app-private data.
- Capability permissions are separate, revocable and least-privilege. Pairing does not automatically enable monitoring or filtering.

## LAN discovery safeguards (0.11.2-dev)

- NSD discovery is opt-in and can be stopped by the user.
- A DNS-SD service name, IP address, port, or service type is untrusted metadata and never authenticates a peer.
- Do not connect to discovered endpoints or send QR tokens until the mutual-authentication protocol and TLS certificate/pinning policy are implemented and reviewed.
- The current discovery component only lists candidate services; it does not advertise the local device, open sockets, or exchange app data.
- Validate all service metadata and treat resolution callbacks as untrusted input.

## Screen-share transmission gate (0.11.1-dev)

Before any future frame is sent, the implementation must verify all seven conditions in ScreenShareAuthorization: verified peer identity, mutual pairing approval, capability grants on both devices, current MediaProjection consent from the sharer, current-session approval from the receiver, authenticated encrypted transport, and an active visible foreground notification.

The gate is a policy helper only; it is not yet wired to a network boundary. No frame transmission is implemented. The receiver must have a visible stop control, and stopping on either side, system revocation, timeout, or transport loss must end the session and release capture resources.


## Bounded transport parser safeguards (0.11.8-dev)

- Parse only the exact supported protocol version and enumerated message types.
- Reject packets larger than the fixed header plus 64 KiB before processing payload contents.
- Require the request identifier to use the fixed 22-character URL-safe format.
- Require the declared payload length to match the remaining packet bytes exactly; reject truncation and trailing data.
- Copy payload arrays at construction and access boundaries.
- Treat every decoded packet as untrusted. Structural validity is not identity authentication or authorization.
- Do not connect this codec to sockets or allow capability execution until mutual identity authentication, TLS certificate binding, replay protection, approval and revocation are implemented and tested.


## Pinned peer identity trust policy (0.11.9-dev)

- Trust only an EC public key whose canonical encoded bytes and SHA-256 fingerprint match an active local trusted-device record.
- Reject revoked, unknown, malformed, non-canonical, oversized and non-EC keys.
- For the X.509 leaf, check validity dates, reject CA certificates as device identities, and require digital-signature key usage when the extension is present.
- Apply the same active-peer pin policy to client and server certificate checks.
- The trust manager is not yet wired to an SSLContext or socket. No TLS session or remote action is currently possible.
- The pin is meaningful only when the registry record came from a completed transcript-bound handshake with explicit approval by both devices. Never populate trust records from LAN discovery or an unverified certificate.
- Before shipping mutual TLS, add a local KeyManager backed by the Android Keystore identity, verify private-key/certificate correspondence, enforce modern TLS protocol versions, bind the session to the pairing transcript and test rejection/revocation on physical devices.

### Important limitation of the pinned trust manager

The current `PinnedPeerTrustManager` is an identity-pin adapter, not a complete X.509 path validator. It does not validate a certificate authority chain or a DNS hostname, and it is not installed in an `SSLContext`. Its decision is safe only when the pinned public key was previously established by the full transcript-bound, mutually approved pairing flow. Android's raw `SSLSocket` API does not perform hostname verification automatically; any future socket integration must explicitly bind the connection to the intended active peer identity and perform the appropriate endpoint verification. Never solve a verification error by accepting every certificate or using a trust-all manager.


## Mutual TLS configuration foundation (0.11.10-dev)

- Present the local EC identity through the Android Keystore X.509 key manager; never export the private key.
- Before TLS use, prove the Keystore private key matches the local certificate by signing a fresh random challenge and verifying it with the certificate public key.
- Scope each TLS trust manager to one exact expected active peer fingerprint. Do not accept a different device merely because it is also in the registry.
- Prefer TLS 1.3 and allow TLS 1.2 as the minimum. Never enable TLS 1.0 or TLS 1.1.
- Server-side socket configuration must require a client certificate.
- A TLS context is not proof that a network session exists. Do not send capabilities or screen frames until the complete pairing, replay protection, socket lifecycle, mutual authentication and explicit capability approval are implemented and tested.
- The pinned identity policy does not validate public CA chains or DNS hostnames. Treat the previously approved peer fingerprint as the identity boundary and never silently fall back to trust-all behavior.


## Transcript-bound handshake safeguards (0.11.11-dev)

- Sign the complete handshake frame, including protocol version, message kind, sender role, sequence, request ID, expiry, transcript hash, sender/recipient fingerprints and fresh nonce.
- Verify the signature against the exact public key assigned to the sender role in the canonical transcript; reject role reflection, peer substitution, transcript substitution and expired invitations.
- Reject malformed or oversized frames before processing signature contents; use the bounded binary decoder.
- Apply replay checks only after signature verification. Require sequence 0 initially and exactly the next sequence for subsequent frames in the same invitation/sender scope.
- The current replay guard is bounded but in-memory and process-local. It does not survive restart or coordinate across peers. Do not claim distributed replay protection until durable redemption and peer-side replay state are implemented.
- A valid device signature is not user consent. Both devices must separately approve pairing and each requested capability.
- Do not connect the handshake to remote commands or screen transmission until the complete token redemption, TLS peer binding, mutual approval, durable replay handling and capability authorization flow is implemented and tested.


## Durable local handshake replay ledger (0.11.12-dev)

- Persist only request IDs, sender fingerprints, expiry values, transcript hashes, sequence counters and SHA-256 nonce hashes. Never persist raw nonces, invitation tokens, private keys or message payloads.
- Encrypt the ledger with AES-256-GCM using an Android Keystore key and a fresh randomized IV for each write. Bind the ciphertext to the versioned format with authenticated additional data.
- Use AtomicFile and fsync. Commit the candidate ledger before changing in-memory replay state; return PERSISTENCE_FAILURE and leave memory unchanged if the write fails.
- Reject malformed, duplicated, oversized or unauthenticated records at startup. Never recover by silently replacing a damaged ledger with an empty one.
- This ledger is local to one installation. It does not provide cross-device replay coordination, distributed token redemption or a network security boundary. App-data deletion, reinstall or Keystore loss requires explicit recovery/re-pairing; it must not silently reset trust.


## QR token redemption protocol (0.11.13-dev)

- Bind each redemption request to the exact issuer-created request ID and expiry; do not accept a different active token as a substitute.
- Validate the canonical 32-byte base64url bearer token, bounded packet lengths and canonical EC P-256 receiver public key before redemption.
- Redeem through the synchronized persistent token registry. A token can be consumed only once; a second attempt must be rejected.
- The redemption payload contains the raw bearer token. Never send it over plaintext TCP, an unverified TLS session, logs, analytics, public channels or an untrusted discovery endpoint.
- Only transmit after mutual TLS has completed and the peer identity is pinned to the expected device. The current codec/verifier is not wired to a socket or TLS session and does not itself provide transport security.
- If pairing is interrupted after successful redemption, keep the token consumed and require a fresh QR invitation. Do not reactivate a consumed token to make retries easier.
- A receiver public key in a redemption request is only a claim. Require the later transcript-bound signature proof and explicit approval on both devices before adding it to the trusted-device registry.


## Pairing transaction recovery (0.11.14-dev)

- Bind transaction state to one request ID, expiry, transcript hash and peer fingerprint.
- Require the existing cryptographic handshake verifier to establish responder identity and signed peer approval; do not accept a remote Boolean as proof of consent.
- Enter PAIRED only when the exact transcript-bound handshake session reports PAIRED and BOTH_APPROVED.
- Persist candidate state before mutating memory; storage failures fail closed.
- Downgrade restored non-terminal attempts to RECOVERY_REQUIRED. Never treat an old in-memory approval as fresh consent after process restart.
- Discard interrupted transactions and require a new QR invitation. A redeemed token remains consumed.
- The current coordinator is not yet wired to Android encrypted persistence, UI, transport or trusted-device insertion.
