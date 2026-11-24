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
