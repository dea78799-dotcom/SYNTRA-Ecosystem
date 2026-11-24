# SYNTRA NEXUS 0.11.11-dev — transcript-bound handshake foundation

## Protocol frame

The handshake frame uses a bounded binary encoding with the `SNH1` marker and protocol version 1. It carries a message kind, sender role, monotonic sequence number, invitation expiry, fixed-format request ID, canonical transcript SHA-256, sender and recipient identity fingerprints, a fresh 32-byte nonce and an ECDSA signature. The signature covers every preceding field using SHA256withECDSA.

The decoder rejects unknown versions and message kinds, malformed identifiers/fingerprints, identical sender/recipient fingerprints, negative counters, truncated input, trailing bytes and signatures above the fixed limit. The existing `SNX1` transport envelope remains the outer packet format.

## Verification

A receiver verifies the exact invitation request ID, expiry and transcript hash; the sender role; the public key assigned to that role in the transcript; both role-bound fingerprints; and the signature. A valid signature for a different transcript, role or peer identity is rejected. Frames expire with the two-minute invitation.

## Replay protection

After signature verification, `NexusHandshakeReplayGuard` enforces sequence 0 for the first frame and exactly the next sequence for each subsequent frame from that sender in that invitation. It prunes expired sessions and has a configurable bounded capacity (default 1,024; hard maximum 4,096).

**Important limitation:** the replay guard is in-memory and local to one process. It does not survive process death, coordinate replay state between peers, redeem the QR token, or replace the persistent one-time token registry. No network socket invokes it yet. Do not treat this milestone as complete distributed replay prevention.

## Not implemented

No LAN socket/listener, QR token redemption protocol, remote user approval exchange, trusted-device registration, TLS handshake, capability execution or screen streaming is enabled by this change. A signed frame is not user consent. The full flow must combine token redemption, TLS peer pinning, both-device approval, durable replay state and explicit capability grants before any remote action is permitted.

No build, unit-test execution, physical-device test or APK publication is claimed by this change.
